const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { spawnSync } = require("child_process");

const HOST = "0.0.0.0";
const PORT = 8790;
const PUBLIC_HOST = "192.168.1.86";
const ROOT = __dirname;
const JOBS_DIR = path.join(ROOT, "windows-build-agent-jobs");
const RESULTS_DIR = path.join(ROOT, "windows-build-agent-results");
const LATEST_ZIP = path.join(RESULTS_DIR, "latest.zip");
const POWERSHELL = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";

const REQUIRED_PHRASES = [
  "\uc720\ud29c\ube0c \ub77c\uc774\ube0c \uacbd\uc7c1\ub825 \uc9c4\ub2e8",
  "\ud575\uc2ec \uc9c4\ub2e8",
  "\ud56d\ubaa9\ubcc4 \uc9c4\ub2e8",
  "\ucd5c\uc885 \ud574\uc11d",
];

fs.mkdirSync(JOBS_DIR, { recursive: true });
fs.mkdirSync(RESULTS_DIR, { recursive: true });

function json(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(`${JSON.stringify(body, null, 2)}\n`);
}

function safeFileName(raw) {
  return decodeURIComponent(raw || "source.zip")
    .replace(/[\\/:\0]/g, "_")
    .replace(/^\.+/, "")
    .trim() || "source.zip";
}

function hashFile(filePath) {
  const hash = crypto.createHash("sha256");
  hash.update(fs.readFileSync(filePath));
  return hash.digest("hex").toUpperCase();
}

function run(command, args, cwd) {
  const result = spawnSync(command, args, {
    cwd,
    encoding: "utf8",
    windowsHide: true,
  });

  const output = `${result.stdout || ""}${result.stderr || ""}`.trim();
  if (result.status !== 0) {
    throw new Error(`${command} failed with exit ${result.status}\n${output}`);
  }
  return output;
}

function psQuote(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function runPowerShell(command, cwd) {
  const encoded = Buffer.from(command, "utf16le").toString("base64");
  return run(POWERSHELL, [
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-EncodedCommand",
    encoded,
  ], cwd);
}

function signExecutable(exePath) {
  return runPowerShell(`
$ErrorActionPreference = "Stop"
$Exe = ${psQuote(exePath)}
$cert = Get-ChildItem Cert:\\CurrentUser\\My |
  Where-Object { $_.Subject -eq "CN=LiveHelper Local Code Signing" -and $_.HasPrivateKey } |
  Select-Object -First 1

if (-not $cert) {
  $cert = New-SelfSignedCertificate \`
    -Type CodeSigningCert \`
    -Subject "CN=LiveHelper Local Code Signing" \`
    -CertStoreLocation Cert:\\CurrentUser\\My \`
    -KeyExportPolicy Exportable \`
    -KeyUsage DigitalSignature \`
    -HashAlgorithm SHA256
}

function Add-ToStoreIfMissing {
  param(
    [string] $StoreName,
    [System.Security.Cryptography.X509Certificates.X509Certificate2] $Certificate
  )

  $store = New-Object System.Security.Cryptography.X509Certificates.X509Store(
    $StoreName,
    [System.Security.Cryptography.X509Certificates.StoreLocation]::CurrentUser
  )
  $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite)
  try {
    $found = $false
    foreach ($existing in $store.Certificates) {
      if ($existing.Thumbprint -eq $Certificate.Thumbprint) {
        $found = $true
        break
      }
    }
    if (-not $found) {
      $store.Add($Certificate)
    }
  } finally {
    $store.Close()
  }
}

Add-ToStoreIfMissing -StoreName "Root" -Certificate $cert
Add-ToStoreIfMissing -StoreName "TrustedPublisher" -Certificate $cert
Set-AuthenticodeSignature -FilePath $Exe -Certificate $cert -HashAlgorithm SHA256 | Out-Null
$embeddedCert = (Get-AuthenticodeSignature -FilePath $Exe).SignerCertificate
if ($embeddedCert) {
  Add-ToStoreIfMissing -StoreName "Root" -Certificate $embeddedCert
  Add-ToStoreIfMissing -StoreName "TrustedPublisher" -Certificate $embeddedCert
}
$signature = Get-AuthenticodeSignature -FilePath $Exe
if ($signature.Status -ne "Valid") {
  throw "EXE signature is not valid: $($signature.Status) $($signature.StatusMessage)"
}
Write-Output "Signed $Exe with $($cert.Thumbprint)"
`, ROOT);
}

function walkDirs(start) {
  const dirs = [start];
  for (let i = 0; i < dirs.length; i += 1) {
    for (const entry of fs.readdirSync(dirs[i], { withFileTypes: true })) {
      if (entry.isDirectory()) {
        dirs.push(path.join(dirs[i], entry.name));
      }
    }
  }
  return dirs;
}

function findSourceDir(extractDir) {
  for (const dir of walkDirs(extractDir)) {
    const required = [
      "LiveHelperWindowsObsRank.cs",
      "YoutubeClient.cs",
      "ComparisonModels.cs",
    ];
    if (required.every((file) => fs.existsSync(path.join(dir, file)))) {
      return dir;
    }
  }
  throw new Error("Could not find Windows OBS source files in uploaded ZIP.");
}

function findCsc() {
  const candidates = [
    "C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe",
    "C:\\Windows\\Microsoft.NET\\Framework\\v4.0.30319\\csc.exe",
  ];
  const found = candidates.find((candidate) => fs.existsSync(candidate));
  if (!found) {
    throw new Error("Windows .NET Framework compiler csc.exe not found.");
  }
  return found;
}

function verifyComparisonModels(sourceDir) {
  const modelPath = path.join(sourceDir, "ComparisonModels.cs");
  const text = fs.readFileSync(modelPath, "utf8");
  const missing = REQUIRED_PHRASES.filter((phrase) => !text.includes(phrase));
  if (missing.length) {
    throw new Error(`ComparisonModels.cs is missing latest report phrases: ${missing.join(", ")}`);
  }
}

function buildUploadedZip(sourceZipPath, originalName) {
  const stamp = new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
  const jobDir = path.join(JOBS_DIR, `job-${stamp}`);
  const extractDir = path.join(jobDir, "extract");
  const resultDir = path.join(jobDir, "result", `LiveHelper_WindowsBuild_Result_${stamp}`);
  const resultZip = path.join(RESULTS_DIR, `LiveHelper_WindowsBuild_Result_${stamp}.zip`);

  fs.mkdirSync(extractDir, { recursive: true });
  fs.mkdirSync(resultDir, { recursive: true });

  const sourceHash = hashFile(sourceZipPath);

  runPowerShell(
    `Expand-Archive -LiteralPath ${psQuote(sourceZipPath)} -DestinationPath ${psQuote(extractDir)} -Force`,
    ROOT
  );

  const sourceDir = findSourceDir(extractDir);
  verifyComparisonModels(sourceDir);

  const distDir = path.join(sourceDir, "dist");
  fs.mkdirSync(distDir, { recursive: true });

  run(findCsc(), [
    "/nologo",
    "/target:winexe",
    "/platform:anycpu",
    "/out:dist\\LiveHelperWindowsObsRank.exe",
    "/reference:System.dll",
    "/reference:System.Core.dll",
    "/reference:System.Drawing.dll",
    "/reference:System.Windows.Forms.dll",
    "/reference:System.Web.Extensions.dll",
    "LiveHelperWindowsObsRank.cs",
    "YoutubeClient.cs",
    "ComparisonModels.cs",
  ], sourceDir);

  const exePath = path.join(distDir, "LiveHelperWindowsObsRank.exe");
  if (!fs.existsSync(exePath)) {
    throw new Error("Build completed but EXE was not created.");
  }

  const signOutput = signExecutable(exePath);
  const exeHash = hashFile(exePath);
  const sourceCopyName = originalName.endsWith(".zip")
    ? originalName
    : "LiveHelperWindowsObsRank_Source_ChannelComparison.zip";
  fs.copyFileSync(sourceZipPath, path.join(resultDir, sourceCopyName));
  fs.copyFileSync(exePath, path.join(resultDir, "LiveHelperWindowsObsRank.exe"));

  const report = [
    "# LiveHelper Windows Build Result",
    "",
    `Build time: ${new Date().toISOString()}`,
    "",
    "## Included Files",
    "",
    "```text",
    sourceCopyName,
    "LiveHelperWindowsObsRank.exe",
    "```",
    "",
    "## Source ZIP SHA-256",
    "",
    "```text",
    sourceHash,
    "```",
    "",
    "## Windows EXE SHA-256",
    "",
    "```text",
    exeHash,
    "```",
    "",
    "## Verification",
    "",
    "```text",
    "ComparisonModels.cs latest report phrases verified.",
    "LiveHelperWindowsObsRank.exe was locally code-signed for this Windows PC.",
    "No source files were modified by the Windows build agent.",
    signOutput,
    "```",
    "",
  ].join("\n");
  fs.writeFileSync(path.join(resultDir, "BUILD_RESULT_KO.md"), report, "utf8");

  if (fs.existsSync(resultZip)) {
    fs.unlinkSync(resultZip);
  }
  runPowerShell(
    `Compress-Archive -LiteralPath ${psQuote(resultDir)} -DestinationPath ${psQuote(resultZip)} -Force`,
    ROOT
  );

  fs.copyFileSync(resultZip, LATEST_ZIP);
  const resultHash = hashFile(resultZip);
  return {
    sourceZip: sourceZipPath,
    sourceHash,
    exePath,
    exeHash,
    resultZip,
    resultHash,
    resultUrl: `http://${PUBLIC_HOST}:${PORT}/results/${path.basename(resultZip)}`,
    latestUrl: `http://${PUBLIC_HOST}:${PORT}/latest.zip`,
  };
}

function serveFile(res, filePath, downloadName) {
  if (!fs.existsSync(filePath)) {
    res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Not found\n");
    return;
  }

  const stat = fs.statSync(filePath);
  res.writeHead(200, {
    "Content-Type": "application/zip",
    "Content-Length": stat.size,
    "Content-Disposition": `attachment; filename="${downloadName}"`,
  });
  fs.createReadStream(filePath).pipe(res);
}

const server = http.createServer((req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);

    if (req.method === "GET" && url.pathname === "/") {
      json(res, 200, {
        service: "LiveHelper Windows Build Agent",
        upload: `curl -T SOURCE.zip http://${PUBLIC_HOST}:${PORT}/build/SOURCE.zip`,
        latest: `http://${PUBLIC_HOST}:${PORT}/latest.zip`,
      });
      return;
    }

    if (req.method === "GET" && url.pathname === "/latest.zip") {
      serveFile(res, LATEST_ZIP, "LiveHelper_WindowsBuild_Result_latest.zip");
      return;
    }

    if (req.method === "GET" && url.pathname.startsWith("/results/")) {
      const name = safeFileName(path.basename(url.pathname));
      serveFile(res, path.join(RESULTS_DIR, name), name);
      return;
    }

    if ((req.method === "PUT" || req.method === "POST") && url.pathname.startsWith("/build/")) {
      const name = safeFileName(path.basename(url.pathname));
      const stamp = new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
      const uploadDir = path.join(JOBS_DIR, `upload-${stamp}`);
      const uploadPath = path.join(uploadDir, name);
      fs.mkdirSync(uploadDir, { recursive: true });

      const out = fs.createWriteStream(uploadPath);
      let handled = false;
      req.pipe(out);
      req.on("error", (err) => json(res, 500, { ok: false, error: err.message }));
      out.on("error", (err) => json(res, 500, { ok: false, error: err.message }));
      out.on("close", () => {
        if (handled) {
          return;
        }
        handled = true;
        try {
          const result = buildUploadedZip(uploadPath, name);
          json(res, 200, { ok: true, ...result });
        } catch (err) {
          json(res, 500, { ok: false, error: err.message });
        }
      });
      return;
    }

    json(res, 404, { ok: false, error: "Not found" });
  } catch (err) {
    json(res, 500, { ok: false, error: err.message });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`LiveHelper Windows Build Agent listening on http://${PUBLIC_HOST}:${PORT}`);
});

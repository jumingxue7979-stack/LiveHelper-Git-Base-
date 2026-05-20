package kr.livehelper.androidliverank;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ComparisonDashboardView extends View {
    private static final float BASE_WIDTH = 420f;
    private static final float BASE_HEIGHT = 1130f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ReportData data = new ReportData();

    public ComparisonDashboardView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setReport(String report) {
        data.parse(report == null ? "" : report);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) width = (int) BASE_WIDTH;
        float scale = width / BASE_WIDTH;
        setMeasuredDimension(width, Math.max(1, Math.round(BASE_HEIGHT * scale)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float scale = width <= 0 ? 1f : width / BASE_WIDTH;
        canvas.save();
        canvas.scale(scale, scale);
        drawDashboard(canvas);
        canvas.restore();
    }

    private void drawDashboard(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(248, 250, 252));
        canvas.drawRect(0, 0, BASE_WIDTH, BASE_HEIGHT, paint);

        paint.setColor(Color.rgb(236, 72, 153));
        canvas.drawRect(0, 0, BASE_WIDTH / 2f, 6, paint);
        paint.setColor(Color.rgb(16, 185, 129));
        canvas.drawRect(BASE_WIDTH / 2f, 0, BASE_WIDTH, 6, paint);

        text(canvas, "LiveRank 핵심 진단", 20, 20, 22, Color.rgb(15, 23, 42), true, Paint.Align.LEFT);
        textFit(canvas, blank(data.keyword, "키워드 확인 불가"), 250, 24, 150, 12, Color.rgb(71, 85, 105), true, Paint.Align.RIGHT);
        textFit(canvas, blank(data.comparisonTitle, "내 채널 vs 비교 대상"), 20, 50, 380, 12, Color.rgb(71, 85, 105), false, Paint.Align.LEFT);

        drawOwnScoreCard(canvas, 20, 84, 184);
        drawCompetitorScoreCard(canvas, 216, 84, 184);
        drawGapCard(canvas, 20, 216, 380);

        text(canvas, "핵심 진단", 20, 306, 18, Color.rgb(15, 23, 42), true, Paint.Align.LEFT);
        drawDiagnosisCards(canvas, 20, 338);

        text(canvas, "3대 카테고리 점수", 20, 590, 18, Color.rgb(15, 23, 42), true, Paint.Align.LEFT);
        text(canvas, "총점 100점", 400, 594, 11, Color.rgb(100, 116, 139), false, Paint.Align.RIGHT);
        text(canvas, "트래픽 60 + 채널 30 + 기본 10", 400, 609, 10, Color.rgb(100, 116, 139), false, Paint.Align.RIGHT);
        card(canvas, 20, 622, 380, 190, Color.WHITE);
        List<Row> rows = data.orderedRows();
        for (int i = 0; i < rows.size() && i < 3; i += 1) {
            drawCategoryRow(canvas, rows.get(i), i, 640 + i * 51);
        }

        drawReactionQuality(canvas, 20, 836);

        card(canvas, 20, 970, 380, 86, Color.rgb(239, 246, 255));
        text(canvas, "참고사항", 36, 984, 13, Color.rgb(37, 99, 235), true, Paint.Align.LEFT);
        multiline(canvas, "본 리포트는 공개 API 기준 진단이며, 1위 진입이나 조회수 상승을 보장하지 않습니다. 다음 방송에서 무엇을 고칠지 확인하는 참고 자료입니다.", 36, 1008, 348, 11, Color.rgb(51, 65, 85), 3);
    }

    private void drawOwnScoreCard(Canvas canvas, float x, float y, float w) {
        card(canvas, x, y, w, 116, Color.rgb(253, 242, 248));
        text(canvas, "내 채널", x + w / 2f, y + 14, 13, Color.rgb(236, 72, 153), true, Paint.Align.CENTER);
        text(canvas, data.ownScore <= 0 ? "-" : String.valueOf(data.ownScore), x + w / 2f, y + 34, 32, Color.rgb(236, 72, 153), true, Paint.Align.CENTER);
        text(canvas, blank(data.ownGrade, "-"), x + w / 2f, y + 74, 15, Color.rgb(30, 41, 59), true, Paint.Align.CENTER);
        textFit(canvas, blank(data.ownChannelTitle, "내 방송"), x + 14, y + 94, w - 28, 10, Color.rgb(100, 116, 139), false, Paint.Align.CENTER);
    }

    private void drawCompetitorScoreCard(Canvas canvas, float x, float y, float w) {
        card(canvas, x, y, w, 116, Color.rgb(236, 253, 245));
        text(canvas, blank(data.competitorLabel, "노필터 라이브 1위"), x + w / 2f, y + 14, 13, Color.rgb(16, 185, 129), true, Paint.Align.CENTER);
        text(canvas, data.competitorScore <= 0 ? "-" : String.valueOf(data.competitorScore), x + w / 2f, y + 34, 32, Color.rgb(16, 185, 129), true, Paint.Align.CENTER);
        text(canvas, blank(data.competitorGrade, "-"), x + w / 2f, y + 74, 15, Color.rgb(30, 41, 59), true, Paint.Align.CENTER);
        textFit(canvas, blank(data.competitorChannelTitle, "1위 채널명 확인 필요"), x + 14, y + 94, w - 28, 11, Color.rgb(15, 23, 42), true, Paint.Align.CENTER);
    }

    private void drawGapCard(Canvas canvas, float x, float y, float w) {
        card(canvas, x, y, w, 62, Color.WHITE);
        int gap = data.competitorScore - data.ownScore;
        text(canvas, "격차", x + 18, y + 13, 13, Color.rgb(15, 23, 42), true, Paint.Align.LEFT);
        String gapText = gap > 0 ? "-" + gap + "점" : "+" + Math.abs(gap) + "점";
        int color = gap > 0 ? Color.rgb(220, 38, 38) : Color.rgb(16, 185, 129);
        text(canvas, gapText, x + 86, y + 10, 28, color, true, Paint.Align.LEFT);
        textFit(canvas, gap > 0 ? "1위가 앞섬 · 개선 필요" : "내 채널 우세", x + 198, y + 22, 164, 12, Color.rgb(71, 85, 105), true, Paint.Align.RIGHT);
    }

    private void drawDiagnosisCards(Canvas canvas, float x, float y) {
        List<Row> rows = data.diagnosisRows();
        if (rows.size() == 0) {
            card(canvas, x, y, 380, 72, Color.WHITE);
            text(canvas, "큰 약점 없음", x + 18, y + 14, 14, Color.rgb(15, 23, 42), true, Paint.Align.LEFT);
            multiline(canvas, "현재 공개 지표상 큰 격차는 없습니다. 낮은 항목 한두 개만 보강하면 됩니다.", x + 18, y + 38, 344, 12, Color.rgb(51, 65, 85), 2);
            return;
        }
        for (int i = 0; i < 3; i += 1) {
            Row row = i < rows.size() ? rows.get(i) : null;
            float top = y + i * 78;
            card(canvas, x, top, 380, 66, Color.WHITE);
            paint.setColor(i == 0 ? Color.rgb(236, 72, 153) : i == 1 ? Color.rgb(249, 115, 22) : Color.rgb(234, 179, 8));
            canvas.drawCircle(x + 24, top + 24, 14, paint);
            text(canvas, String.valueOf(i + 1), x + 24, top + 14, 13, Color.WHITE, true, Paint.Align.CENTER);
            if (row == null) {
                text(canvas, "추가 점검", x + 48, top + 11, 13, Color.rgb(15, 23, 42), true, Paint.Align.LEFT);
                multiline(canvas, "다음 리포트에서 낮은 항목을 이어서 확인하세요.", x + 48, top + 34, 318, 11, Color.rgb(51, 65, 85), 2);
            } else {
                textFit(canvas, diagnosisTitle(row), x + 48, top + 11, 318, 13, Color.rgb(15, 23, 42), true, Paint.Align.LEFT);
                multiline(canvas, diagnosisText(row), x + 48, top + 34, 318, 11, Color.rgb(51, 65, 85), 2);
            }
        }
    }

    private void drawCategoryRow(Canvas canvas, Row row, int index, float y) {
        if (index > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(Color.rgb(226, 232, 240));
            canvas.drawLine(36, y - 8, 384, y - 8, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        textFit(canvas, (index + 1) + ". " + displayName(row.name), 36, y - 3, 344, 12, Color.rgb(15, 23, 42), true, Paint.Align.LEFT);
        text(canvas, "내 " + row.own + "점", 36, y + 17, 10, Color.rgb(100, 116, 139), false, Paint.Align.LEFT);
        bar(canvas, 88, y + 24, 112, 8, row.own, Color.rgb(236, 72, 153));
        text(canvas, blank(data.competitorShortLabel, "1위") + " " + row.competitor + "점", 218, y + 17, 10, Color.rgb(100, 116, 139), false, Paint.Align.LEFT);
        bar(canvas, 268, y + 24, 112, 8, row.competitor, Color.rgb(16, 185, 129));
    }

    private void drawReactionQuality(Canvas canvas, float x, float y) {
        card(canvas, x, y, 380, 110, Color.rgb(255, 251, 235));
        text(canvas, "반응 품질 (참고)", x + 16, y + 14, 14, Color.rgb(15, 23, 42), true, Paint.Align.LEFT);
        badge(canvas, x + 260, y + 11, 104, 24, "총점 미반영", Color.rgb(254, 243, 199), Color.rgb(253, 230, 138), Color.rgb(146, 64, 14));
        String first = data.reactionLines.size() > 0 ? data.reactionLines.get(0) : "채팅 참여율: 라이브 채팅 데이터 연결 전 단계";
        String second = data.reactionLines.size() > 1 ? data.reactionLines.get(1) : "좋아요 반응: 확인 전";
        multiline(canvas, referenceLine(first), x + 16, y + 42, 348, 11, Color.rgb(51, 65, 85), 2);
        multiline(canvas, referenceLine(second), x + 16, y + 74, 348, 11, Color.rgb(51, 65, 85), 2);
    }

    private void card(Canvas canvas, float x, float y, float w, float h, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        RectF rect = new RectF(x, y, x + w, y + h);
        canvas.drawRoundRect(rect, 10, 10, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(Color.rgb(226, 232, 240));
        canvas.drawRoundRect(rect, 10, 10, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void badge(Canvas canvas, float x, float y, float w, float h, String label, int fill, int line, int textColor) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        RectF rect = new RectF(x, y, x + w, y + h);
        canvas.drawRoundRect(rect, 8, 8, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(line);
        canvas.drawRoundRect(rect, 8, 8, paint);
        paint.setStyle(Paint.Style.FILL);
        text(canvas, label, x + w / 2f, y + 5, 10, textColor, true, Paint.Align.CENTER);
    }

    private void bar(Canvas canvas, float x, float y, float w, float h, int score, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(226, 232, 240));
        RectF bg = new RectF(x, y, x + w, y + h);
        canvas.drawRoundRect(bg, h / 2, h / 2, paint);
        paint.setColor(color);
        float filled = Math.max(0, Math.min(100, score)) * w / 100f;
        RectF fg = new RectF(x, y, x + filled, y + h);
        canvas.drawRoundRect(fg, h / 2, h / 2, paint);
    }

    private void text(Canvas canvas, String value, float x, float y, float size, int color, boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setFakeBoldText(bold);
        paint.setTextAlign(align);
        canvas.drawText(value == null ? "" : value, x, y + size, paint);
        paint.setFakeBoldText(false);
    }

    private void textFit(Canvas canvas, String value, float x, float y, float width, float size, int color, boolean bold, Paint.Align align) {
        String fitted = fit(value, width, size, bold);
        float drawX = align == Paint.Align.RIGHT ? x + width : align == Paint.Align.CENTER ? x + width / 2f : x;
        text(canvas, fitted, drawX, y, size, color, bold, align);
    }

    private void multiline(Canvas canvas, String value, float x, float y, float width, float size, int color, int maxLines) {
        String[] words = (value == null ? "" : value).split(" ");
        String line = "";
        int printed = 0;
        paint.setTextSize(size);
        paint.setFakeBoldText(false);
        for (String word : words) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(next) > width && line.length() > 0) {
                text(canvas, line, x, y + printed * (size + 6), size, color, false, Paint.Align.LEFT);
                printed += 1;
                line = word;
                if (printed >= maxLines) return;
            } else {
                line = next;
            }
        }
        if (printed < maxLines && line.length() > 0) {
            text(canvas, line, x, y + printed * (size + 6), size, color, false, Paint.Align.LEFT);
        }
    }

    private String fit(String value, float width, float size, boolean bold) {
        String clean = value == null ? "" : value.trim();
        paint.setTextSize(size);
        paint.setFakeBoldText(bold);
        if (paint.measureText(clean) <= width) {
            paint.setFakeBoldText(false);
            return clean;
        }
        String suffix = "...";
        int end = clean.length();
        while (end > 0 && paint.measureText(clean.substring(0, end) + suffix) > width) {
            end -= 1;
        }
        paint.setFakeBoldText(false);
        return end <= 0 ? suffix : clean.substring(0, end) + suffix;
    }

    private String displayName(String label) {
        if (label.startsWith("트래픽")) return "트래픽 질량 (60점 묶음)";
        if (label.startsWith("채널")) return "채널 영향 (30점 묶음)";
        if (label.startsWith("기본")) return "기본 최적화 (10점 묶음)";
        return label == null || label.trim().length() == 0 ? "확인 필요" : label.trim();
    }

    private String diagnosisTitle(Row row) {
        String label = row.name;
        if (label.startsWith("트래픽")) return "트래픽 질량 부족";
        if (label.startsWith("채널")) return "채널 영향 부족";
        if (label.startsWith("기본")) return "기본 최적화 부족";
        return "추가 점검";
    }

    private String diagnosisText(Row row) {
        int gap = Math.max(0, row.competitor - row.own);
        String competitor = blank(data.competitorShortLabel, "1위");
        String label = row.name;
        if (label.startsWith("트래픽")) {
            return "현재 방송 트래픽이 " + competitor + " 대비 " + gap + "점 낮음. 방송 시작 직후 유입 동선 점검이 필요합니다.";
        }
        if (label.startsWith("채널")) {
            return "채널 영향이 " + competitor + "보다 " + gap + "점 낮음. 반복 방송과 다음 방송 예고를 쌓아야 합니다.";
        }
        if (label.startsWith("기본")) {
            return "기본 최적화가 " + competitor + "보다 " + gap + "점 낮음. 제목·설명 첫 부분에 [" + blank(data.keyword, "검색 키워드") + "]를 분명히 넣으세요.";
        }
        return "이전 형식 항목은 3대 카테고리 점수에 섞지 않고 새 분석에서 다시 확인합니다.";
    }

    private String blank(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    private String referenceLine(String value) {
        String text = value == null || value.trim().length() == 0 ? "확인 전" : value.trim();
        return text.contains("총점 미반영") ? text : text + " · 총점 미반영";
    }

    private static final class ReportData {
        String keyword = "";
        String comparisonTitle = "";
        String ownChannelTitle = "";
        String competitorChannelTitle = "";
        String competitorLabel = "노필터 라이브 1위";
        String competitorShortLabel = "1위";
        int ownScore = 0;
        int competitorScore = 0;
        String ownGrade = "";
        String competitorGrade = "";
        final List<Row> rows = new ArrayList<>();
        final List<String> reactionLines = new ArrayList<>();

        void parse(String report) {
            rows.clear();
            reactionLines.clear();
            keyword = "";
            comparisonTitle = "";
            ownChannelTitle = "";
            competitorChannelTitle = "";
            competitorLabel = "노필터 라이브 1위";
            competitorShortLabel = "1위";
            ownScore = 0;
            competitorScore = 0;
            ownGrade = "";
            competitorGrade = "";

            String[] lines = report.split("\\n");
            Row current = null;
            boolean inReactionQuality = false;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.length() == 0) {
                    inReactionQuality = false;
                    continue;
                }
                if (line.startsWith("키워드:")) keyword = line.substring(4).trim();
                if (line.startsWith("내 채널:")) ownChannelTitle = line.substring(5).trim();
                if (line.contains("내 채널") && line.contains("노필터 라이브") && !line.startsWith("내 채널:")) comparisonTitle = line;
                if (line.startsWith("비교 대상:")) {
                    competitorLabel = line.substring(6).trim();
                    competitorShortLabel = shortRank(competitorLabel);
                }
                Matcher competitorName = Pattern.compile("^(노필터 라이브 \\d+위|비교 대상):\\s*(.+)$").matcher(line);
                if (competitorName.find() && !line.startsWith("비교 대상:")) {
                    competitorLabel = competitorName.group(1);
                    competitorShortLabel = shortRank(competitorLabel);
                    competitorChannelTitle = competitorName.group(2).trim();
                }
                Matcher own = Pattern.compile("^내 채널\\s+(\\d+)점\\(([^)]+)\\)").matcher(line);
                if (own.find()) {
                    ownScore = intValue(own.group(1));
                    ownGrade = own.group(2);
                }
                Matcher comp = Pattern.compile("^(?:노필터 라이브 \\d+위|1위 채널|비교 대상)\\s+(\\d+)점\\(([^)]+)\\)").matcher(line);
                if (comp.find()) {
                    competitorScore = intValue(comp.group(1));
                    competitorGrade = comp.group(2);
                }
                if (line.equals("반응 품질 참고")) {
                    inReactionQuality = true;
                    current = null;
                    continue;
                }
                if (inReactionQuality && line.startsWith("-")) {
                    reactionLines.add(line.substring(1).trim());
                    continue;
                }
                Matcher rowStart = Pattern.compile("^\\d+\\.\\s*(.+?)\\s{2,}(핵심 약점|개선 권장|내 강점|동등)").matcher(line);
                if (rowStart.find()) {
                    if (!isScoreCategory(rowStart.group(1).trim())) {
                        current = null;
                        continue;
                    }
                    current = new Row();
                    current.name = rowStart.group(1).trim();
                    rows.add(current);
                    continue;
                }
                if (current != null && line.startsWith("내")) {
                    current.own = extractScore(line);
                    continue;
                }
                if (current != null && (line.startsWith("1위") || line.startsWith("2위") || line.startsWith("3위") || line.startsWith("4위") || line.startsWith("5위") || line.startsWith("6위") || line.startsWith("7위") || line.startsWith("8위") || line.startsWith("9위") || line.startsWith("비교"))) {
                    current.competitor = extractScore(line);
                }
            }
        }

        List<Row> orderedRows() {
            ArrayList<Row> ordered = new ArrayList<>();
            String[] order = { "traffic", "channel", "basic" };
            for (String key : order) {
                addFirst(ordered, key);
            }
            for (Row row : rows) {
                if (!ordered.contains(row) && isScoreCategory(row.name)) ordered.add(row);
            }
            return ordered;
        }

        List<Row> diagnosisRows() {
            ArrayList<Row> result = new ArrayList<>();
            for (Row row : orderedRows()) {
                if (row.competitor - row.own > 0) {
                    result.add(row);
                    if (result.size() >= 3) break;
                }
            }
            return result;
        }

        private void addFirst(ArrayList<Row> ordered, String key) {
            for (Row row : rows) {
                if (!ordered.contains(row) && matches(row.name, key)) {
                    ordered.add(row);
                    return;
                }
            }
        }

        private boolean matches(String label, String key) {
            if ("traffic".equals(key)) return label.startsWith("트래픽");
            if ("channel".equals(key)) return label.startsWith("채널");
            if ("basic".equals(key)) return label.startsWith("기본");
            return false;
        }

        private boolean isScoreCategory(String label) {
            String text = label == null ? "" : label.trim();
            return text.startsWith("트래픽") || text.startsWith("채널") || text.startsWith("기본");
        }

        private int extractScore(String line) {
            Matcher matcher = Pattern.compile("(\\d+)점").matcher(line);
            return matcher.find() ? intValue(matcher.group(1)) : 0;
        }

        private int intValue(String value) {
            try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
        }

        private String shortRank(String label) {
            Matcher matcher = Pattern.compile("(\\d+)위").matcher(label == null ? "" : label);
            return matcher.find() ? matcher.group(1) + "위" : "1위";
        }
    }

    private static final class Row {
        String name = "";
        int own = 0;
        int competitor = 0;
    }
}

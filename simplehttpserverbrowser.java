import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import javax.swing.text.html.HTMLDocument;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class simplehttpserverbrowser extends JFrame {

    private JComboBox<String> methodCombo;
    private JTextField urlField;
    private JTextField postDataField;
    private JButton goButton;
    private JEditorPane webPane;
    private JTextArea rawTextArea;
    private JLabel statsLabel;
    private JTabbedPane tabbedPane;
    private boolean loading;

    public simplehttpserverbrowser() {
        setTitle("VKU Simple HTTP Browser - Lập trình mạng");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initComponents();
    }

    private void initComponents() {
        // 1. TOP PANEL: Thanh địa chỉ, chọn phương thức và dữ liệu POST
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel urlBarPanel = new JPanel(new BorderLayout(5, 5));
        methodCombo = new JComboBox<>(new String[]{"GET", "POST", "HEAD"});
        urlField = new JTextField("http://example.com");
        goButton = new JButton("Go");

        urlBarPanel.add(methodCombo, BorderLayout.WEST);
        urlBarPanel.add(urlField, BorderLayout.CENTER);
        urlBarPanel.add(goButton, BorderLayout.EAST);

        // Panel phụ để nhập dữ liệu khi chọn POST
        JPanel postPanel = new JPanel(new BorderLayout(5, 5));
        postPanel.add(new JLabel("Dữ liệu POST (key1=val1&key2=val2): "), BorderLayout.WEST);
        postDataField = new JTextField("name=SinhVienVKU&course=LapTrinhMang");
        postDataField.setEnabled(false);
        postPanel.add(postDataField, BorderLayout.CENTER);

        topPanel.add(urlBarPanel, BorderLayout.NORTH);
        topPanel.add(postPanel, BorderLayout.SOUTH);

        // Bật/tắt ô nhập dữ liệu POST tùy theo phương thức được chọn
        methodCombo.addActionListener(e -> {
            String selected = (String) methodCombo.getSelectedItem();
            postDataField.setEnabled("POST".equals(selected));
        });

        // 2. CENTER PANEL: Biểu diễn trang HTML (JEditorPane) & Xem mã nguồn/Header
        tabbedPane = new JTabbedPane();

        // Tab 1: Hiển thị trang Web (Theo Gợi ý 1 trong bài giảng)
        webPane = new JEditorPane();
        webPane.setEditable(false);
        webPane.setContentType("text/html");
        
        // Triển khai HyperlinkListener như gợi ý trong Slide 29
        webPane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    if (e.getURL() != null) {
                        String newUrl = e.getURL().toString();
                        urlField.setText(newUrl);
                        methodCombo.setSelectedItem("GET");
                        executeRequest();
                    }
                }
            }
        });

        // Tab 2: Hiển thị Raw HTML hoặc thông tin HEAD
        rawTextArea = new JTextArea();
        rawTextArea.setEditable(false);
        rawTextArea.setFont(new Font("Monospaced", Font.PLAIN, 13));

        tabbedPane.addTab("Biểu diễn trang HTML (Browser View)", new JScrollPane(webPane));
        tabbedPane.addTab("Thông tin Header / Mã nguồn HTML", new JScrollPane(rawTextArea));

        // 3. BOTTOM PANEL: Hiển thị thống kê phân tích thẻ HTML
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        bottomPanel.setBackground(new Color(240, 240, 240));
        statsLabel = new JLabel("Sẵn sàng. Hãy nhập URL và nhấn Go.");
        statsLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        bottomPanel.add(statsLabel, BorderLayout.CENTER);

        // Gắn sự kiện cho nút Go và phím Enter trên thanh địa chỉ
        goButton.addActionListener(e -> executeRequest());
        urlField.addActionListener(e -> executeRequest());

        // Thêm các thành phần vào JFrame
        add(topPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void executeRequest() {
        // Chụp dữ liệu trên luồng giao diện, không đọc ô nhập từ luồng mạng.
        if (loading) return;
        String address = urlField.getText().trim();
        if (address.isEmpty()) return;
        if (!address.contains("://")) address = "http://" + address;
        final String finalUrl = address;
        final String method = (String) methodCombo.getSelectedItem();
        final String payload = postDataField.getText();
        urlField.setText(address);
        loading = true;
        goButton.setEnabled(false);
        methodCombo.setEnabled(false);
        statsLabel.setText("Đang gửi " + method + " ...");

        // doInBackground chạy riêng; done chạy trên luồng giao diện Swing.
        new SwingWorker<Response, Void>() {
            @Override
            protected Response doInBackground() throws Exception {
                return sendRequest(finalUrl, method, payload);
            }

            @Override
            protected void done() {
                try {
                    Response result = get();
                    rawTextArea.setText(result.headers + "\n" + result.body);
                    rawTextArea.setCaretPosition(0);
                    urlField.setText(result.url.toString());
                    if ("HEAD".equals(method)) {
                        // Xóa trang cũ; HEAD tuyệt đối không đọc thân phản hồi.
                        webPane.setText("");
                        tabbedPane.setSelectedIndex(1);
                        statsLabel.setText("HEAD: " + result.status + " | Không đọc thân phản hồi");
                    } else {
                        if (result.isHtml) {
                            webPane.setContentType("text/html");
                            // Tạo document mới để không giữ charset/base của trang cũ.
                            HTMLDocument document = (HTMLDocument)webPane.getEditorKit().createDefaultDocument();
                            document.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
                            document.setBase(result.url);
                            webPane.setDocument(document);
                            // Dùng chính HTML đã tải để render và thống kê cùng một phản hồi.
                            webPane.setText(result.body);
                        } else {
                            webPane.setContentType("text/plain");
                            webPane.setText(result.body);
                        }
                        webPane.setCaretPosition(0);
                        tabbedPane.setSelectedIndex(0);
                        statsLabel.setText(result.status + " | " + result.byteLength
                                + " bytes | " + result.body.codePointCount(0, result.body.length())
                                + " ký tự Unicode" + (result.isHtml
                                ? " | p: " + countHtmlTag(result.body, "p")
                                + " | div: " + countHtmlTag(result.body, "div")
                                + " | span: " + countHtmlTag(result.body, "span")
                                + " | img/imag: " + (countHtmlTag(result.body, "img")
                                + countHtmlTag(result.body, "imag"))
                                : " | Nội dung không phải HTML"));
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    statsLabel.setText("Lỗi: " + cause.getMessage());
                    JOptionPane.showMessageDialog(simplehttpserverbrowser.this,
                            cause.getMessage(), "Lỗi gửi yêu cầu", JOptionPane.ERROR_MESSAGE);
                } finally {
                    loading = false;
                    goButton.setEnabled(true);
                    methodCombo.setEnabled(true);
                }
            }
        }.execute();
    }

    // Lớp nhỏ chứa kết quả để chuyển từ luồng mạng sang giao diện.
    static class Response {
        URL url;
        String status;
        String headers;
        String body = "";
        int byteLength;
        boolean isHtml;
    }

    static Response sendRequest(String address, String method, String payload) throws Exception {
        URL url = new URL(address);
        if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
            throw new IOException("Chỉ hỗ trợ địa chỉ http:// hoặc https://");
        }
        URLConnection baseConnection = url.openConnection();
        HttpURLConnection connection = (HttpURLConnection) baseConnection;
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "SimpleStudentBrowser/1.0");
            connection.setRequestProperty("Accept-Encoding", "identity");
            // Giữ nguyên phương thức: hiển thị 3xx và Location để người dùng xem.
            connection.setInstanceFollowRedirects(false);
            if ("POST".equals(method)) {
                byte[] data = encodeForm(payload).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(data);
                }
            }
            int code = connection.getResponseCode();
            Response result = new Response();
            result.url = connection.getURL();
            result.status = connection.getHeaderField(0);
            StringBuilder headers = new StringBuilder();
            headers.append("URL: ").append(result.url).append("\n");
            headers.append(method).append(" - ").append(result.status).append("\n");
            // Duyệt theo chỉ số để giữ từng dòng header, kể cả header trùng tên.
            for (int i = 1; connection.getHeaderField(i) != null; i++) {
                headers.append(connection.getHeaderFieldKey(i)).append(": ").append(connection.getHeaderField(i)).append("\n");
            }
            result.headers = headers.toString();
            if ("HEAD".equals(method)) return result;

            String type = connection.getContentType();
            result.isHtml = type != null && (type.toLowerCase(java.util.Locale.ROOT).startsWith("text/html") || type.toLowerCase(java.util.Locale.ROOT).startsWith("application/xhtml+xml"));
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream != null) {
                // Đọc nguyên byte, không dùng readLine vì sẽ thay đổi dấu xuống dòng.
                try (BufferedInputStream in = new BufferedInputStream(stream);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        bytes.write(buffer, 0, n);
                    }
                    byte[] data = bytes.toByteArray();
                    result.byteLength = data.length;
                    result.body = new String(data, findCharset(type));
                }
            }
            return result;
        } finally {
            // Luôn giải phóng kết nối, kể cả khi xảy ra lỗi.
            connection.disconnect();
        }
    }

    static Charset findCharset(String contentType) {
        if (contentType != null) {
            Matcher m = Pattern.compile("charset=\\s*[\"']?([^;\\s\"']+)",
                    Pattern.CASE_INSENSITIVE).matcher(contentType);
            if (m.find()) return Charset.forName(m.group(1));
        }
        // Quy ước đơn giản của bài: thiếu charset thì dùng UTF-8.
        return StandardCharsets.UTF_8;
    }

    static String encodeForm(String form) throws Exception {
        if (form.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String pair : form.split("&", -1)) {
            if (result.length() > 0) result.append('&');
            String[] parts = pair.split("=", 2);
            // Giải mã rồi mã hóa lại: tránh biến %20 thành %2520 hoặc + thành %2B.
            String key = URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], "UTF-8") : "";
            result.append(URLEncoder.encode(key, "UTF-8")).append('=').append(URLEncoder.encode(value, "UTF-8"));
        }
        return result.toString();
    }

    /**
     * Hàm đếm số lượng thẻ mở HTML (không phân biệt chữ hoa/thường)
     * Ví dụ: khớp với cả <p>, <p class="text">, <P>... nhưng không khớp nhầm <param>
     */
    static int countHtmlTag(String html, String tagName) {
        // Bỏ comment và nội dung script/style/textarea/title để tránh đếm chữ giả thẻ.
        html = html.replaceAll("(?s)<!--.*?(?:-->|$)", "");
        html = html.replaceAll("(?is)<(script|style|textarea|title)(?=\\s|>)[^>]*>.*?(?:</\\1\\s*>|$)", "");
        String regex = "<" + tagName + "(?=\\s|>|/>)";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new simplehttpserverbrowser().setVisible(true);
        });
    }
}
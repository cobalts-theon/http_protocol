import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

// Server kiểm thử dùng thư viện có sẵn trong JDK, không cần Internet.
public class BrowserTest {
    static final String HTML = "<html><body><!-- <p>giả</p> --><DIV><p>Xin chào</p>\r\n"
            + "<P>Hai</P><span>Việt Nam</span><img src=''><imag></DIV>"
            + "<script>var x='<p>';</script><a href='/next'>Trang sau</a></body></html>";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8000), 0);
        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String html = HTML;
            if (method.equals("POST")) {
                java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = exchange.getRequestBody().read(buffer)) != -1) body.write(buffer, 0, n);
                String form = new String(body.toByteArray(), StandardCharsets.UTF_8);
                html = "<html><body><p>POST thành công</p><div>" + form
                        .replace("&", "&amp;").replace("<", "&lt;") + "</div><a href='/next'>Tiếp</a></body></html>";
            } else if (path.equals("/next")) {
                html = "<html><body><p>Trang tiếp theo</p><a href='/'>Về đầu</a></body></html>";
            }
            byte[] data = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Server", "StudentTestServer");
            exchange.getResponseHeaders().set("Last-Modified", "Mon, 26 Oct 2026 00:00:00 GMT");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
            int status = path.equals("/missing") ? 404 : path.equals("/redirect") ? 302 : 200;
            if (status == 302) exchange.getResponseHeaders().set("Location", "/next");
            exchange.sendResponseHeaders(status, method.equals("HEAD") ? -1 : data.length);
            if (!method.equals("HEAD")) exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.start();
        if (args.length > 0 && args[0].equals("serve")) {
            System.out.println("Server: http://127.0.0.1:8000/ (Ctrl+C để dừng)");
            return;
        }
        try {
            String url = "http://127.0.0.1:8000/";
            simplehttpserverbrowser.Response get = simplehttpserverbrowser.sendRequest(url, "GET", "");
            check(get.body.equals(HTML), "Giữ nguyên HTML và xuống dòng");
            check(get.byteLength == HTML.getBytes(StandardCharsets.UTF_8).length, "Đúng số byte UTF-8");
            check(simplehttpserverbrowser.countHtmlTag(get.body, "p") == 2, "Đếm p, bỏ comment/script");
            check(simplehttpserverbrowser.countHtmlTag(get.body, "div") == 1, "Đếm DIV");
            check(simplehttpserverbrowser.countHtmlTag(get.body, "span") == 1, "Đếm span");
            check(simplehttpserverbrowser.countHtmlTag(get.body, "img") == 1, "Đếm img");
            check(simplehttpserverbrowser.countHtmlTag(get.body, "imag") == 1, "Đếm imag");
            simplehttpserverbrowser.Response head = simplehttpserverbrowser.sendRequest(url, "HEAD", "");
            check(head.body.isEmpty() && head.byteLength == 0 && head.headers.contains("Content-length"), "HEAD chỉ đọc header");
            String form = "name=Nguyễn Văn A&city=Đà+Nẵng&text=a%26b%3Dc";
            check(simplehttpserverbrowser.encodeForm(simplehttpserverbrowser.encodeForm(form))
                    .equals(simplehttpserverbrowser.encodeForm(form)), "Không mã hóa hai lần");
            simplehttpserverbrowser.Response post = simplehttpserverbrowser.sendRequest(url, "POST", form);
            check(post.body.contains("POST thành công") && post.body.contains("Nguy%E1%BB%85n"), "POST tiếng Việt");
            check(simplehttpserverbrowser.sendRequest(url + "missing", "GET", "").status.contains("404"), "Đọc trang lỗi 404");
            check(simplehttpserverbrowser.sendRequest(url + "redirect", "POST", "a=b").status.contains("302"), "Giữ phản hồi chuyển hướng");
            System.out.println("Tất cả kiểm thử đã đạt.");
        } finally {
            server.stop(0);
        }
    }

    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        System.out.println("Đạt: " + name);
    }
}

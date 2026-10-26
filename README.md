# Bài tập: Tạo HTTP Browser đơn giản bằng Java

## 1. Kế hoạch và thiết kế

Bám theo `Bài 7-2.pdf`: slide 9–15 về GET/HEAD/POST; slide 19–25 về URL, URLConnection, luồng vào và URLEncoder; slide 28 là đề bài; slide 29 gợi ý Swing và HyperlinkListener.

Chọn Swing vì có sẵn trong JDK, không cài thêm thư viện. Giữ tên lớp/file `simplehttpserverbrowser` của bản ban đầu để chạy thuận tiện.

Thứ tự thực hiện:

1. Tạo JFrame, thanh URL, JComboBox GET/POST/HEAD, nút Go và ô dữ liệu POST.
2. Tạo hai tab: JEditorPane để xem trang và JTextArea để xem header cùng mã nguồn; thêm JLabel thống kê ở dưới.
3. Gắn sự kiện Go/Enter và HyperlinkListener. Link được mở bằng GET, cập nhật thanh URL; link tương đối dựa trên URL của HTMLDocument.
4. Dùng SwingWorker gửi yêu cầu ngoài luồng giao diện. Chụp URL, phương thức và body trước khi chạy worker. Chặn gửi chồng khi đang tải.
5. Đọc status/header, xử lý riêng HEAD; đọc nguyên byte đối với GET/POST, giải mã thành chuỗi rồi phân tích HTML.
6. Cập nhật giao diện trong done(), đóng stream bằng try-with-resources, disconnect trong finally và thông báo lỗi.
7. Biên dịch và kiểm tra với server cục bộ, sau đó thử URL Internet nếu có kết nối.

Một file chính gồm:

| Thành phần | Công việc |
| --- | --- |
| `simplehttpserverbrowser extends JFrame` | Giao diện và điều khiển |
| `initComponents()` | Bố trí GUI và gắn sự kiện |
| `executeRequest()` | Chạy SwingWorker và cập nhật kết quả |
| `sendRequest()` | Giao tiếp bằng URL → URLConnection → HttpURLConnection |
| `Response` | Chứa URL, status, headers, body, số byte và loại nội dung |
| `encodeForm()` | Mã hóa từng tên/giá trị trong form |
| `findCharset()` | Lấy charset từ Content-Type, mặc định UTF-8 |
| `countHtmlTag()` | Đếm các thẻ mở bằng regex |

Bố cục:

```text
GET/POST/HEAD | URL                         | Go
Dữ liệu POST: name=Nguyễn Văn A&city=Đà Nẵng
------------------------------------------------
[Trang HTML] [Header / Mã nguồn]
                 Nội dung
------------------------------------------------
Status | bytes | ký tự Unicode | p/div/span/img
```

## 2. Luồng mạng

### GET

- Tạo URL, chỉ chấp nhận HTTP/HTTPS, gọi openConnection rồi ép sang HttpURLConnection.
- Đặt GET, timeout kết nối/đọc 10 giây, User-Agent và Accept-Encoding: identity.
- getResponseCode() bắt đầu yêu cầu; đọc status line thật bằng getHeaderField(0).
- Đọc từng header bằng chỉ số, giữ riêng cả các header trùng tên.
- Dùng getInputStream() khi bình thường, getErrorStream() cho lỗi từ 400 trở lên.
- BufferedInputStream đọc từng khối vào ByteArrayOutputStream; không dùng readLine vì sẽ làm thay đổi CR/LF và chiều dài.
- Đếm, hiển thị HTML hoặc hiển thị văn bản nếu Content-Type không phải HTML.

### POST

- Tách body theo `&`, mỗi cặp tách ở dấu `=` đầu tiên.
- URLDecoder giải mã rồi URLEncoder mã hóa từng tên và giá trị theo UTF-8, tránh mã hóa lặp `%20` thành `%2520`.
- setDoOutput(true), Content-Type: application/x-www-form-urlencoded; charset=UTF-8.
- Chuyển body sang byte UTF-8; setFixedLengthStreamingMode đặt độ dài byte; ghi bằng OutputStream rồi đóng stream.
- Đọc và hiển thị phản hồi giống GET.

Nhập `name=Nguyễn Văn A&city=Đà Nẵng` hoặc chuỗi form đã mã hóa. Theo quy tắc form: `+` là khoảng trắng; dấu cộng thật nhập `%2B`, dấu & trong giá trị nhập `%26`, dấu % thật nhập `%25`.

### HEAD

- setRequestMethod("HEAD"), lấy status và tất cả headers.
- Trả kết quả ngay, không gọi getInputStream/getErrorStream và không render HTML.
- Content-Length là độ dài tài nguyên do server khai báo, không phải số byte body đã tải. Server có thể không cung cấp một số header; chương trình chỉ hiển thị những gì thực sự nhận được.

Chương trình không tự theo chuyển hướng để tránh POST bị đổi thành GET và để xem được phản hồi 3xx. Khi gặp 301/302/303/307/308, xem Location trong tab header rồi nhập URL đích; Location tương đối được ghép với URL hiện tại.

## 3. Chiều dài và regex

- Byte: `data.length`, là số byte thân phản hồi đọc thực tế, không gồm status/header hoặc framing HTTP.
- Ký tự Unicode: `body.codePointCount(0, body.length())`. Ví dụ emoji có thể chiếm hai phần tử char nhưng một code point. Đây không phải số ký tự hiển thị theo cụm dấu.
- Giải mã theo charset trong Content-Type; thiếu charset mặc định UTF-8. Không tự dò charset trong meta HTML. Độ dài byte vẫn đúng khi charset bị khai báo sai.
- Regex cho thẻ p: `<p(?=\s|>|/>)`, bật CASE_INSENSITIVE. Tương tự cho div/span/img/imag. Lookahead ngăn đếm nhầm `<param>` hoặc `<picture>`; không khớp `</p>`.
- Bỏ comment và vùng script/style/textarea/title trước khi đếm. Cộng img và imag để đáp ứng cả tên chuẩn và cách viết trong slide.

Giới hạn cần hiểu khi báo cáo: regex phù hợp HTML thông thường của bài tập, không phải bộ phân tích HTML đầy đủ. HTML sai cấu trúc, ký tự `<p>` nằm trong thuộc tính có dấu nháy hoặc cấu trúc đặc biệt có thể làm sai số. Không thể cam kết chính xác cho mọi HTML tùy ý chỉ bằng regex đơn giản. Số thẻ là thống kê mã nguồn, không phải DOM do JavaScript tạo ra.

JEditorPane hỗ trợ HTML/CSS cơ bản, không chạy JavaScript và không hiển thị trang hiện đại như Chrome. Dùng HTMLDocument.setBase để link/ảnh tương đối hoạt động; setText dùng chính phản hồi vừa nhận, thay cho setPage gây tải thêm một lần và có thể khác nội dung thống kê. Ảnh của trang có thể tạo yêu cầu tài nguyên phụ. Việc tải HTTP chính chạy trong SwingWorker; render Swing vẫn diễn ra trên luồng giao diện nên trang rất lớn có thể chậm. Bài tập giữ toàn bộ body trong RAM.

## 4. Biên dịch và chạy

Cách chạy đơn giản nhất: dùng JDK 18 trở lên (mặc định UTF-8), mở terminal ngay trong thư mục chứa file Java:

```sh
javac simplehttpserverbrowser.java
java simplehttpserverbrowser
```

Hai lệnh trên tạo file `.class` ngay cạnh file `.java`, không cần tạo thư mục. Nếu dùng JDK cũ với bảng mã mặc định khác UTF-8, cần thêm `-encoding UTF-8` khi biên dịch để đọc đúng tiếng Việt.

Trên JDK mới có thể xuất hiện cảnh báo constructor URL đã deprecated; vẫn dùng URL theo bài giảng, tương thích Java 8.

## 5. Kiểm thử

Biên dịch server kiểm thử và chạy tự động:

```sh
javac BrowserTest.java
java BrowserTest
```

Đặt `BrowserTest.java` và `simplehttpserverbrowser.java` trong cùng thư mục. Mở terminal tại thư mục đó; javac sẽ tự biên dịch lớp browser được tham chiếu nếu cần.

Giữ server chạy để thử trên GUI (terminal thứ nhất):

```sh
java BrowserTest serve
```

Terminal thứ hai chạy browser bằng lệnh ở trên. Server dùng cổng 8000; Ctrl+C để dừng.

| Phương thức / URL | Dữ liệu / thao tác | Kết quả mong đợi |
| --- | --- | --- |
| GET http://127.0.0.1:8000/ | Go hoặc Enter | p=2, div=1, span=1, img/imag=2; tiếng Việt đúng; không đếm comment/script |
| GET http://127.0.0.1:8000/next | Hoặc click Trang sau | URL cập nhật, p=1; click Về đầu quay lại |
| POST http://127.0.0.1:8000/ | name=Nguyễn Văn A&city=Đà Nẵng | HTML xác nhận POST, form đã mã hóa; p=1, div=1 |
| POST http://127.0.0.1:8000/ | text=a%26b%3Dc&plus=%2B | Giữ ý nghĩa dấu & / = / + trong giá trị |
| HEAD http://127.0.0.1:8000/ | Go | Status, Content-Type, Content-Length, Last-Modified, Server, Date; không đọc body |
| GET http://127.0.0.1:8000/missing | Go | Status 404 và vẫn đọc được HTML lỗi |
| POST http://127.0.0.1:8000/redirect | a=b | Status 302, Location: /next; không gửi GET tự động |
| GET http://127.0.0.1:1/ | Go | Thông báo lỗi, nút Go được bật lại |
| GET ftp://localhost/ | Go | Thông báo chỉ hỗ trợ HTTP/HTTPS |

URL Internet có thể thử thêm: GET/HEAD `https://example.com/`; POST `https://httpbin.org/post` với `name=SinhVien&course=LapTrinhMang`. Đây là endpoint minh họa chưa được xác minh truy cập trong môi trường này; httpbin thường trả JSON, nên browser hiển thị văn bản và không thống kê HTML. Server cục bộ là cách kiểm tra POST trả HTML ổn định.

Kết quả trong môi trường soạn bài: đã biên dịch thành công mã chính với `--release 8` và biên dịch cả server kiểm thử bằng JDK 25. Chưa chạy được kiểm thử HTTP vì sandbox chặn mở socket (`Operation not permitted`); chưa kiểm tra GUI trực tiếp vì không có môi trường desktop ảo. Không xem các bước này là đã đạt kiểm thử tích hợp.

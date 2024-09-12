package org.apache.coyote.http11;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.model.domain.User;
import com.techcourse.model.dto.UserRegistration;
import org.apache.catalina.session.Session;
import org.apache.catalina.session.SessionManager;
import org.apache.coyote.Processor;
import org.apache.coyote.http.cookie.HttpCookie;
import org.apache.coyote.http.cookie.HttpCookies;
import org.apache.coyote.http11.request.HttpMethod;
import org.apache.coyote.http11.request.HttpRequest;
import org.apache.coyote.http11.request.RequestHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Http11Processor implements Runnable, Processor {

    private static final String STATIC = "/static";
    private static final String HTML = ".html";
    private static final String CSS = ".css";
    private static final String JS = ".js";
    private static final String SVG = ".svg";

    private static final Logger log = LoggerFactory.getLogger(Http11Processor.class);

    private final SessionManager sessionManager = SessionManager.getInstance();
    private final Socket connection;

    public Http11Processor(final Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.info("connect host: {}, port: {}", connection.getInetAddress(), connection.getPort());
        process(connection);
    }

    @Override
    public void process(final Socket connection) {
        try (final var inputStream = connection.getInputStream();
             final var outputStream = connection.getOutputStream()) {

            performProcess(inputStream, outputStream);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void performProcess(final InputStream inputStream, final OutputStream outputStream) throws IOException {
        try {
            HttpRequest httpRequest = new HttpRequest(inputStream);

            final String method = httpRequest.getHttpMethod().name();
            final String requestURL = httpRequest.getPath();

            if (method.equals(HttpMethod.GET.name())) {
                doGet(httpRequest, requestURL, outputStream);
            }

            if (method.equals(HttpMethod.POST.name())) {
                doPost(httpRequest, requestURL, outputStream);
            }
        } catch (IOException | IllegalArgumentException e) {
            log.error(e.getMessage(), e);
            handleException(outputStream);
        }
    }

    private void handleException(OutputStream outputStream) {
        try {
            sendHttpResponse("/static/500.html", outputStream, "500 INTERNAL SERVER ERROR", "text/html;charset=utf-8", "");
        } catch (IOException e) {
            log.error("Failed to send error response", e);
        }
    }

    private void doPost(final HttpRequest httpRequest,
                        final String requestURL, final OutputStream outputStream) throws IOException {

        Map<String, String> requestBody = httpRequest.getRequestBody().getValues();
        if (requestURL.equals("/login")) {
            login(httpRequest.getRequestHeader(), outputStream, requestBody);
        }

        if (requestURL.equals("/register")) {
            boolean isregistered = register(requestBody);
            if (isregistered) {
                redirect("", "/", outputStream);
                return;
            }
            sendHttpResponse("/static/register.html", outputStream, "400 BAD REQUEST", "text/html;charset=utf-8", "");
        }
    }

    private void login(final RequestHeader httpRequestHeaders, final OutputStream outputStream, final Map<String, String> requestBody) throws IOException {
        String cookies = httpRequestHeaders.getCookies();

        Optional<Session> httpSession = loginPost(requestBody);
        String[] result = loginGet(httpSession.isPresent());
        String httpStatus = result[0];
        HttpCookies httpCookies = HttpCookies.from(cookies);
        String cookieHeader = "";

        if (httpSession.isPresent() && (!httpCookies.hasJsessionId() || sessionManager.findSession(httpCookies.getJsessionId()) == null)) {
            HttpCookie cookie = new HttpCookie("JSESSIONID", httpSession.get().getId().toString());
            httpCookies.addCookie(cookie);
            cookieHeader = setCookieHeader(cookie.getValue());
        }

        if (httpStatus.equals("302 FOUND")) {
            redirect(cookieHeader, "/", outputStream);
            return;
        }

        redirect(cookieHeader, "/401", outputStream);
    }

    private String setCookieHeader(String uuid) {
        return "Set-Cookie: JSESSIONID=" + uuid + " " + "\r\n"
                + "";
    }

    private boolean register(Map<String, String> requestBody) {
        String account = requestBody.get("account");
        String password = requestBody.get("password");
        String email = requestBody.get("email");

        return validateRegistration(account, password, email);
    }

    private boolean validateRegistration(final String account, final String password, final String email) {
        try {
            InMemoryUserRepository.findByAccount(account)
                    .ifPresentOrElse(
                            user -> {
                                throw new IllegalArgumentException("이미 존재하는 계정명입니다.");
                            },
                            () -> {
                                UserRegistration userRegisterInfo = new UserRegistration(account, password, email);
                                User newUser = InMemoryUserRepository.save(userRegisterInfo);
                                log.info("user: {}의 회원가입이 완료되었습니다.", newUser.getAccount());
                            }
                    );
            return true;
        } catch (IllegalArgumentException e) {
            log.info("오류 발생: {}", e.getMessage());
            return false;
        }
    }

    private void doGet(final HttpRequest httpRequest, final String requestURL,
                       final OutputStream outputStream) throws IOException {
        String cookies = httpRequest.getRequestHeader().getHeaders().get("Cookie");
        String[] searchResourcePath = determineResourcePath(requestURL);

        String sessionId = "";
        HttpCookies httpCookies = HttpCookies.from(cookies);

        if (httpCookies.hasJsessionId()) {
            sessionId = httpCookies.getJsessionId();
        }

        if (sessionManager.findSession(sessionId) != null && requestURL.equals("/login")) {
            redirect("", "/", outputStream);
        }

        String httpStatus = searchResourcePath[0];
        String resourcePath = searchResourcePath[1];

        String contentType = determineContentType(resourcePath);

        sendHttpResponse(resourcePath, outputStream, httpStatus, contentType, "");
    }

    private String[] determineResourcePath(String requestURL) {
        String[] result = new String[2];
        result[0] = "200 OK";

        if (requestURL.equals("/") || requestURL.equals("/index.html")) {
            result[1] = "/static/index.html";
            return result;
        }

        if (requestURL.endsWith(SVG)) {
            result[1] = STATIC + "/assets/img/error-404-monochrome.svg";
            return result;
        }

        if (requestURL.endsWith(HTML) || requestURL.endsWith(CSS) || requestURL.endsWith(JS)) {
            result[1] = STATIC + requestURL;
            return result;
        }

        result[1] = STATIC + requestURL + HTML;
        return result;
    }

    private String determineContentType(String resourcePath) {
        if (resourcePath.endsWith(CSS)) {
            return "text/css";
        }

        if (resourcePath.endsWith(JS)) {
            return "application/javascript";
        }

        if (resourcePath.endsWith(SVG)) {
            return "image/svg+xml";
        }

        return "text/html;charset=utf-8";
    }

    private Optional<Session> loginPost(Map<String, String> requestBody) {
        Map<String, String> userInfo = parseUserInfo(requestBody);
        String uuid = null;

        try {
            uuid = validateUser(userInfo);
        } catch (IllegalArgumentException e) {
            log.info("오류 발생: {}", e.getMessage());
        }

        if (uuid != null) {
            return Optional.ofNullable(SessionManager.getInstance().findSession(uuid));
        }

        return Optional.empty();
    }

    private String[] loginGet(boolean isLogined) {
        if (!isLogined) {
            return new String[]{"401 UNAUTHORIZED", "/401"};
        }
        return new String[]{"302 FOUND", "/index"};
    }

    private Map<String, String> parseUserInfo(Map<String, String> requestBody) {
        Map<String, String> userInfo = new HashMap<>();
        if (requestBody.get("account").isEmpty() || requestBody.get("password").isEmpty()) {
            throw new IllegalArgumentException("필수 입력값이 비어 있습니다.");
        }

        userInfo.put("account", requestBody.get("account"));
        userInfo.put("password", requestBody.get("password"));

        return userInfo;
    }

    private String validateUser(Map<String, String> userInfo) {
        String account = userInfo.get("account");
        String password = userInfo.get("password");

        Optional<User> loginUser = InMemoryUserRepository.findByAccount(account);

        if (loginUser.isEmpty()) {
            log.info(account + "는(은) 등록되지 않은 계정입니다.");
            return null;
        }

        User user = loginUser.get();

        String uuid = sessionManager.findSessionId(user);

        if (user.checkPassword(password)) {
            if (uuid == null) {
                uuid = createNewSession(user);
            }
            log.info("로그인 성공. user : {}", user);
            return uuid;
        }

        log.info(account + "의 비밀번호가 잘못 입력되었습니다.");
        return null;
    }

    private String createNewSession(final User user) {
        String uuid = UUID.randomUUID().toString();

        Session session = new Session(uuid);
        session.setAttribute("user", user);

        sessionManager.add(session);

        return uuid;
    }

    private void redirect(String cookieHeader, String path, OutputStream outputStream) throws IOException {
        String locationHeader = "Location: " + path;

        String[] resourcePath = determineResourcePath(path);

        URL resource = getClass().getResource(resourcePath[1]);

        byte[] responseBody = Files.readAllBytes(new File(resource.getFile()).toPath());

        String header = String.join("\r\n",
                "HTTP/1.1 302 FOUND ",
                "Content-Type: text/html;charset=utf-8  ",
                "Content-Length: " + responseBody.length + " ",
                locationHeader,
                cookieHeader,
                "");

        byte[] headerBytes = header.getBytes();
        byte[] response = new byte[headerBytes.length + responseBody.length];

        System.arraycopy(headerBytes, 0, response, 0, headerBytes.length);
        System.arraycopy(responseBody, 0, response, headerBytes.length, responseBody.length);

        writeHttpResponse(response, outputStream);
    }

    private void sendHttpResponse(final String path, final OutputStream outputStream, String httpStatus,
                                  final String contentType, final String cookie) throws IOException {

        byte[] response = createHttpResponse(httpStatus, contentType, cookie, path);
        writeHttpResponse(response, outputStream);
    }

    private byte[] createHttpResponse(String httpStatus, String contentType, String cookieHeader, final String path) throws IOException {
        URL resource = getClass().getResource(path);

        if (resource == null) {
            resource = getClass().getResource("/static/404.html");
            httpStatus = "404 NOT FOUND";
        }

        byte[] responseBody = Files.readAllBytes(new File(resource.getFile()).toPath());

        String header = String.join("\r\n",
                "HTTP/1.1 " + httpStatus + " ",
                "Content-Type: " + contentType + " ",
                "Content-Length: " + responseBody.length + " ",
                cookieHeader,
                "");

        byte[] headerBytes = header.getBytes();
        byte[] response = new byte[headerBytes.length + responseBody.length];

        System.arraycopy(headerBytes, 0, response, 0, headerBytes.length);
        System.arraycopy(responseBody, 0, response, headerBytes.length, responseBody.length);

        return response;
    }

    private void writeHttpResponse(byte[] response, OutputStream outputStream) throws IOException {
        outputStream.write(response);
        outputStream.flush();
    }
}

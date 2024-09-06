# 만들면서 배우는 스프링

## 톰캣 구현하기

### 구현하면서 궁금했던 점

1. Http11Processor의 이 부분을

``` 
String line = bufferedReader.readLine();

line = line.split(" ")[1];

final URL resource = getClass().getResource("/static" + line);
```

``` 
String line = bufferedReader.readLine();

line = line.split(" ")[1];

final URL resource = getClass().getResource("/static/index.html");
```

이렇게 적으면 `localhost:8080/index.html`을 호출했을 때 디스플레이 되는 페이지 모습이 달랐습니다.
(전자: 그래프 노출되지 않음)
(후자: 그래프 노출됨)

정확한 이유가 뭘까요? 🤔

<br><br><br><br><br>

### 학습목표

- 웹 서버 구현을 통해 HTTP 이해도를 높인다.
- HTTP의 이해도를 높혀 성능 개선할 부분을 찾고 적용할 역량을 쌓는다.
- 서블릿에 대한 이해도를 높인다.
- 스레드, 스레드풀을 적용해보고 동시성 처리를 경험한다.

### 시작 가이드

1. 미션을 시작하기 전에 파일, 입출력 스트림 학습 테스트를 먼저 진행합니다.
    - [File, I/O Stream](study/src/test/java/study)
    - 나머지 학습 테스트는 다음 강의 시간에 풀어봅시다.
2. 학습 테스트를 완료하면 LMS의 1단계 미션부터 진행합니다.

## 학습 테스트

1. [File, I/O Stream](study/src/test/java/study)
2. [HTTP Cache](study/src/test/java/cache)
3. [Thread](study/src/test/java/thread)

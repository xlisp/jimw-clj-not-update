FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/jimw-clj.jar /jimw-clj/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/jimw-clj/app.jar"]

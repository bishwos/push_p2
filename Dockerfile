FROM maven:3-eclipse-temurin-11-alpine

COPY . /root/p2
WORKDIR /root/p2
RUN mvn install
RUN mvn package

CMD ["java", "-jar", "/root/p2/target/p2-0.3.2.jar", "-c", "/root/config/config.json"]

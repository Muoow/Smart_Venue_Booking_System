FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY Smart_Venue_Booking_System/pom.xml .

RUN mvn -B dependency:go-offline

COPY Smart_Venue_Booking_System/src ./src

RUN mvn -B package -DskipTests

FROM eclipse-temurin:21

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
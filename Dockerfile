# --- Build ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# --- Runtime: Payara Server 6 Full ---
FROM payara/server-full:6.2024.6-jdk17

# Driver JDBC de PostgreSQL en el classpath del servidor
ADD --chmod=644 https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar \
    /opt/payara/appserver/glassfish/lib/postgresql.jar

# Pool + recurso JDBC (jdbc/taller) creados al arrancar
COPY --chown=payara:payara docker/post-boot-commands.asadmin /opt/payara/config/post-boot-commands.asadmin

COPY --from=build --chown=payara:payara /build/target/taller.war /opt/payara/deployments/taller.war

EXPOSE 8080 4848

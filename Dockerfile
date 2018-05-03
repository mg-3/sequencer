####################################################
# Arrow API Order Number Generator
#
# Build image with:
#    docker build -t arrow/order-num-generator:v1 .
#
# Run image with:
#    docker run --name ong -d -p 8090:8090 arrow/order-num-generator:v1
#
# NOTES:
# Config directory with files is needed before running
# the build. Also the tar.gz must be in the current
# directory.
####################################################
FROM openjdk:8u141-jre-slim
MAINTAINER Mike Garcia "mikegarcia@arrow.com"
RUN apt-get update && apt-get install -y net-tools vim less
RUN mkdir logs
WORKDIR /home/arrow/ong
ADD target/*.jar /home/arrow/ong/
ADD config/* /home/arrow/ong/config/
VOLUME ["./logs"]
EXPOSE 10010
CMD ["java", "-Dlogging.file=config/logback.xml", "-jar", "OrderNumberGenerator.jar"]

#!/bin/sh
mvn clean install -DskipTests=true
mvn package spring-boot:repackage -Pboot
docker rmi -f quay.io/redcapcloud/hapi-fhir-server:latest
docker image prune -f
docker build -t hapi-fhir/hapi-fhir-jpaserver-starter .


@echo off
rem Local development: seeds demo data and uses the dev profile. Set DB_PASSWORD first (see application-dev.properties).
cd /d "%~dp0"
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev

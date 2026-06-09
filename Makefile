run:
	mvn spring-boot:run

install:
	mvn clean install

tests:
	./mvnw test

build:
	docker compose --env-file .env.docker up -d

rebuild:
	docker compose --env-file .env.docker up -d --build

logs:
	docker compose logs -f

down:
	docker compose down


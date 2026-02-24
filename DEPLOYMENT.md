# Развёртывание CardBot на сервере

## Варианты хостинга

| Провайдер | Минимум | Плюсы |
|-----------|---------|-------|
| **DigitalOcean** | $4–6/мес | Простой, есть Docker |
| **Hetzner** | ~€4/мес | Дешёвый, EU |
| **Timeweb Cloud** | ~300₽/мес | Русский интерфейс |
| **Selectel** | ~200₽/мес | Российский |
| **Oracle Cloud** | Бесплатно | Free tier, сложнее настройка |

Рекомендуемый минимум: **1 CPU, 1 GB RAM, 25 GB SSD**.

---

## Шаг 1. Подготовка сервера

### 1.1 Подключение по SSH

```bash
ssh root@ВАШ_IP_АДРЕС
# или
ssh ubuntu@ВАШ_IP_АДРЕС
```

### 1.2 Обновление системы (Ubuntu/Debian)

```bash
apt update && apt upgrade -y
```

### 1.3 Установка Docker и Docker Compose

```bash
# Docker
curl -fsSL https://get.docker.com | sh

# Добавить пользователя в группу docker (если не root)
usermod -aG docker $USER

# Docker Compose (встроен в Docker Desktop, для Linux — отдельно)
apt install docker-compose-plugin -y

# Проверка
docker --version
docker compose version
```

### 1.4 Firewall (рекомендуется)

```bash
# Разрешить только SSH
ufw allow 22
ufw enable

# PostgreSQL (5432) не открывать — бот подключается внутри Docker-сети.
# По умолчанию порт 5432 доступен с localhost хоста.
```

---

## Шаг 2. Загрузка проекта на сервер

### Вариант A: Через Git (рекомендуется)

```bash
# На сервере
cd /opt  # или /home/ubuntu
git clone https://github.com/ВАШ_USERNAME/CardBot.git
cd CardBot
```

### Вариант B: Через SCP (если нет Git-репозитория)

```bash
# С локального компьютера
scp -r /Users/dmitrijkarpov/Desktop/KarpProjectJava/CardBot root@ВАШ_IP:/opt/
```

Исключите из копирования: `target/`, `.git/`, `.idea/`. Минимально нужны:
- `src/`
- `pom.xml`
- `docker-compose.yml`
- `Dockerfile`
- `.dockerignore`
- `.env.example` (для примера)

---

## Шаг 3. Создание .env на сервере

```bash
cd /opt/CardBot  # или куда скопировали

cp .env.example .env
nano .env
```

Заполните:

```
BOT_TOKEN=ваш_токен_от_BotFather
DB_URL=jdbc:postgresql://postgres:5432/cardbot
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

**Важно:** Смените `DB_PASSWORD` на сложный пароль в production.

---

## Шаг 4. Запуск

```bash
cd /opt/CardBot
docker compose up -d --build
```

Первый запуск займёт 3–5 минут (сборка JAR).

### Проверка

```bash
# Статус контейнеров
docker compose ps

# Логи бота
docker compose logs -f cardbot
```

В логах должно быть что-то вроде: `Bot is ready to receive updates`.

---

## Шаг 5. Автозапуск при перезагрузке сервера

Docker с `restart: unless-stopped` уже перезапускает контейнеры при падении. При перезагрузке сервера Docker-демон поднимает контейнеры автоматически.

Проверка:

```bash
# Перезагрузить сервер
reboot

# После входа снова
docker compose ps
```

---

## Полезные команды

```bash
# Остановить
docker compose down

# Запустить снова (данные сохраняются)
docker compose up -d

# Обновить после изменений в коде
git pull  # или загрузить файлы заново
docker compose up -d --build

# Логи
docker compose logs -f cardbot
docker compose logs -f postgres

# Зайти в контейнер
docker exec -it cardbot sh
```

---

## Безопасность

1. **Пароль PostgreSQL** — смените `DB_PASSWORD` в `.env` на сложный.
2. **Порт 5432** — не открывайте его в firewall наружу (доступ только внутри Docker).
3. **BOT_TOKEN** — храните только в `.env`, не коммитьте в Git.
4. **SSH** — по возможности используйте ключи вместо пароля и отключите вход под root.

---

## Troubleshooting

### Бот не отвечает в Telegram

- Проверьте логи: `docker compose logs cardbot`
- Убедитесь, что `BOT_TOKEN` в `.env` верный
- Проверьте, что контейнер запущен: `docker compose ps`

### Ошибка подключения к БД

- Дождитесь готовности PostgreSQL: `docker compose logs postgres`
- Убедитесь, что `DB_URL` использует хост `postgres`, а не `localhost`

### Не хватает памяти при сборке

- Увеличьте swap на сервере или соберите образ локально и загрузите на сервер

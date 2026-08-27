# coreoflow — Instalação em Servidor Hetzner

Guia de instalação da plataforma coreoflow num servidor Hetzner com Ubuntu 22.04 LTS.

---

## Requisitos do servidor

- VPS Hetzner CX22 ou superior (2 vCPU, 4 GB RAM recomendado)
- Ubuntu 22.04 LTS
- Acesso SSH como root

---

## 1. Atualizar o sistema

```bash
apt update && apt upgrade -y
```

---

## 2. Instalar Java 17

```bash
apt install -y openjdk-17-jdk
java -version
# openjdk version "17.x.x"
```

---

## 3. Instalar MySQL

```bash
apt install -y mysql-server
systemctl enable mysql
systemctl start mysql

# Configuração inicial segura
mysql_secure_installation
```

### Criar base de dados e utilizador

```sql
mysql -u root -p

CREATE DATABASE coreoflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'coreoflow_app'@'localhost' IDENTIFIED BY 'NKgi406DS25!#*';
GRANT ALL PRIVILEGES ON coreoflow.* TO 'coreoflow_app'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

---

## 4. Criar utilizador do sistema

```bash
useradd -m -s /bin/bash coreoflow
```

---

## 5. Fazer o build da aplicação (na máquina de desenvolvimento)

```bash
# Na tua máquina local, dentro da pasta do projeto:
./mvnw clean package -Pproduction -DskipTests

# O JAR gerado fica em:
# target/coreoflow-0.0.1-SNAPSHOT.jar
```

Copia o JAR para o servidor:

```bash
scp target/coreoflow-0.0.1-SNAPSHOT.jar root@IP_DO_SERVIDOR:/opt/coreoflow/coreoflow.jar
```

---

## 6. Criar pasta da aplicação

```bash
mkdir -p /opt/coreoflow/uploads/logos
chown -R coreoflow:coreoflow /opt/coreoflow
```




---

## 7. Configurar o ficheiro de propriedades

Cria o ficheiro de configuração em `/opt/coreoflow/application.properties`:

```bash
nano /opt/coreoflow/application.properties
```

Conteúdo:

```properties
# BASE DE DADOS
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/coreoflow?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=coreoflow_app
spring.datasource.password=PASSWORD_FORTE_AQUI
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.jpa.hibernate.ddl-auto=update

# EMAIL (SMTP)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=noreply@coreoflow.pt
spring.mail.password=APP_PASSWORD_GMAIL
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
app.suporte.email=suporte@coreoflow.pt

# LOGOS
app.logos.dir=/opt/coreoflow/uploads/logos
spring.web.resources.static-locations=classpath:/META-INF/resources/,classpath:/resources/,classpath:/static/,classpath:/public/,file:/opt/coreoflow/uploads/

# SERVIDOR
server.port=8080
```

---

## 8. Criar serviço systemd

```bash
nano /etc/systemd/system/coreoflow.service
```

Conteúdo:

```ini
[Unit]
Description=coreoflow
After=network.target mysql.service

[Service]
User=coreoflow
WorkingDirectory=/opt/coreoflow
ExecStart=/usr/bin/java -jar /opt/coreoflow/coreoflow.jar \
  --spring.config.location=file:/opt/coreoflow/application.properties
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=coreoflow

[Install]
WantedBy=multi-user.target
```

Ativar e arrancar:

```bash
systemctl daemon-reload
systemctl enable coreoflow
systemctl start coreoflow

# Verificar estado
systemctl status coreoflow

# Ver logs em tempo real
journalctl -u coreoflow -f
```

---

## 9. Configurar Nginx como proxy reverso

```bash
apt install -y nginx
```

Criar configuração:

```bash
nano /etc/nginx/sites-available/coreoflow
```

Conteúdo (substitui `app.coreoflow.pt` pelo teu domínio):

```nginx
server {
    listen 80;
    server_name app.coreoflow.pt;

    client_max_body_size 20M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600;
        proxy_send_timeout 3600;
    }
}
```

Ativar:

```bash
ln -s /etc/nginx/sites-available/coreoflow /etc/nginx/sites-enabled/
nginx -t
systemctl reload nginx
```

---

## 10. Certificado SSL (HTTPS) com Let's Encrypt

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d app.coreoflow.pt

# Renovação automática (já configurada pelo certbot)
certbot renew --dry-run
```

---

## 11. Firewall

```bash
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw enable
ufw status
```

---

## 12. Deploy automático via GitHub Actions

O deploy é feito automaticamente sempre que há um merge para o branch `production`.

### 12.1. Configurar secrets no GitHub

No repositório GitHub, vai a **Settings → Secrets and variables → Actions** e adiciona:

| Secret | Valor |
|--------|-------|
| `HETZNER_HOST` | IP do servidor Hetzner |
| `HETZNER_USER` | `root` (ou utilizador com sudo) |
| `HETZNER_SSH_KEY` | Chave SSH privada (conteúdo do `~/.ssh/id_rsa`) |

### 12.2. Gerar e autorizar a chave SSH

Na tua máquina local:

```bash
# Gerar chave (se ainda não tens)
ssh-keygen -t rsa -b 4096 -C "github-actions-coreoflow"

# Copiar chave pública para o servidor
ssh-copy-id -i ~/.ssh/id_rsa.pub root@IP_DO_SERVIDOR

# Copiar chave privada para o secret HETZNER_SSH_KEY
cat ~/.ssh/id_rsa
```

### 12.3. Criar o workflow

O ficheiro `.github/workflows/deploy.yml` já está incluído no repositório. O pipeline faz:

1. Build com Maven em modo produção
2. Upload do JAR para o servidor via SCP
3. Reinício do serviço systemd

### 12.4. Fluxo de trabalho

```
feature/xxx  →  merge  →  main  →  merge  →  production  →  deploy automático
```

O deploy só dispara no branch `production`. Nunca faças push direto para esse branch — usa sempre Pull Request a partir de `main`.

---

## Estrutura de pastas no servidor

```
/opt/coreoflow/
├── coreoflow.jar          ← JAR da aplicação
├── application.properties  ← Configuração (não versionar!)
└── uploads/
    └── logos/              ← Logótipos dos estúdios
```

---

## Resolução de problemas

**Ver logs da aplicação:**
```bash
journalctl -u coreoflow -n 100 --no-pager
```

**Reiniciar serviço:**
```bash
systemctl restart coreoflow
```

**Verificar porta:**
```bash
ss -tlnp | grep 8080
```

**Testar ligação à BD:**
```bash
mysql -u coreoflow_app -p coreoflow
```

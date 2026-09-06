#!/usr/bin/env python3
"""
Deploy do site comercial do CoreoFlow (coreoflow.me) para o servidor.

Copia os ficheiros estáticos para /var/www/coreoflow e instala o server block
do nginx. Idempotente — pode correr sempre que o site mudar.

Uso (a partir de C:\\dev\\coreoflow\\site):
    ! python deploy-site.py

O SSH direto para produção está bloqueado no Claude Code, por isso corre isto tu.
"""
import os
import paramiko

HOST = "188.245.115.89"
USER = "root"
PWD = "gtecsam"

HERE = os.path.dirname(os.path.abspath(__file__))
DOCROOT = "/var/www/coreoflow"
ASSETS = ["index.html", "logo-coreoflow.png", "logo2-coreoflow.png", "favicon.ico"]
NGINX_CONF_LOCAL = os.path.join(HERE, "nginx-coreoflow-site.conf")
NGINX_CONF_REMOTE = "/etc/nginx/sites-available/coreoflow-site"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PWD, timeout=25)


def run(cmd):
    _in, out, err = c.exec_command(cmd, timeout=60)
    o = out.read().decode("utf-8", "replace").strip()
    e = err.read().decode("utf-8", "replace").strip()
    rc = out.channel.recv_exit_status()
    if o:
        print(o)
    if e:
        print("[stderr]", e)
    return rc


print(">>> docroot")
run(f"mkdir -p {DOCROOT} && chown -R www-data:www-data {DOCROOT}")

print(">>> upload dos ficheiros")
sftp = c.open_sftp()
for name in ASSETS:
    local = os.path.join(HERE, name)
    sftp.put(local, f"{DOCROOT}/{name}")
    print("   ", name, os.path.getsize(local), "bytes")
sftp.put(NGINX_CONF_LOCAL, NGINX_CONF_REMOTE)
sftp.close()

print(">>> ativar server block")
run(f"ln -sf {NGINX_CONF_REMOTE} /etc/nginx/sites-enabled/coreoflow-site")
run(f"chown -R www-data:www-data {DOCROOT}")

print(">>> nginx -t")
if run("nginx -t") != 0:
    print("!!! nginx -t falhou — NÃO recarreguei. Corrige e volta a correr.")
    c.close()
    raise SystemExit(1)

print(">>> reload nginx")
run("systemctl reload nginx")

print(
    "\n=== FEITO ===\n"
    "Ficheiros em /var/www/coreoflow, server block ativo para coreoflow.me / www.\n\n"
    "Falta (uma vez):\n"
    "  1. No GoDaddy, apontar o DNS para o servidor:\n"
    "       Tipo A   Nome @     Valor 188.245.115.89\n"
    "       Tipo A   Nome www   Valor 188.245.115.89\n"
    "     (remover o reencaminhamento / parking que existe agora)\n"
    "  2. Quando o DNS propagar (verifica: nslookup coreoflow.me), gerar o certificado:\n"
    "       ! ssh root@188.245.115.89 \"certbot --nginx -d coreoflow.me -d www.coreoflow.me --redirect -n --agree-tos -m geral@coreoflow.me\"\n"
)
c.close()

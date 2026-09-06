# Site comercial — coreoflow.me

Landing page estática para vender o CoreoFlow. Sem build: é `index.html` + 3 imagens.

## Ficheiros
- `index.html` — a página (CSS e JS inline, tema claro/escuro conforme o browser)
- `logo-coreoflow.png` / `logo2-coreoflow.png` — logótipo (branco / navy)
- `favicon.ico`
- `nginx-coreoflow-site.conf` — server block para `coreoflow.me` + `www`
- `deploy-site.py` — envia tudo para `/var/www/coreoflow` no servidor e ativa o nginx

## Preview local
```
cd site && python -m http.server 8899
# abrir http://127.0.0.1:8899
```

## Publicar
```
cd site
! python deploy-site.py
```
Depois, uma única vez:
1. **GoDaddy** → apontar `coreoflow.me` e `www` (tipo A) para `188.245.115.89`
   e remover o reencaminhamento/parking atual.
2. Quando o DNS propagar, gerar o certificado HTTPS:
   ```
   ! ssh root@188.245.115.89 "certbot --nginx -d coreoflow.me -d www.coreoflow.me --redirect -n --agree-tos -m geral@coreoflow.me"
   ```

## A rever antes de divulgar
- **Preços**: as tabelas dizem "Sob proposta" — ver `<!-- TODO Daniel -->` no `index.html`
  se quiseres mostrar valores.
- Email de contacto: `geral@coreoflow.me` (usado em todos os botões).

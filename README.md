# usuario-api

Microserviço de usuários do Nexo: contas, autenticação (JWT + refresh token em cookie
HttpOnly), perfil, alunos e professores. Porta padrão: **8081**.

## Rodando localmente

```bash
cp .env.example .env        # preencha NEXO_JWT_SECRET e DB_PASSWORD
docker compose up -d        # Postgres do serviço
./mvnw spring-boot:run
```

`NEXO_JWT_SECRET` precisa ser **o mesmo do monólito**: é o que faz o token emitido aqui
valer lá. Com `NEXO_SEED_ENABLED=true` (só dev, nunca com o perfil `prod`) nascem as contas
`diretor`, `professor`, `aluno` e `admin`, todas com a senha `123456`.

## Com o front Angular

O `ng serve` usa o `nexo/proxy.conf.json`: login, perfil, contas, alunos e professores vão
para este serviço (8081); o restante da API, para o monólito (8080). Suba os dois backends e
rode `npm start` em `nexo/`.

## Rotas

| Rota | Quem |
|---|---|
| `POST /api/auth/login`, `/refresh` | público |
| `POST /api/auth/logout`, `GET /api/auth/me` | autenticado |
| `GET/PATCH /api/usuarios/me`, `POST/DELETE /api/usuarios/me/foto`, `POST /api/usuarios/me/senha[/validar]` | autenticado |
| `GET /api/fotos/{id}` | público (id é UUID) |
| `GET /api/admin/contas`, `PATCH .../{id}/ativo`, `POST .../{id}/senha-provisoria` | ADMIN, DIRETOR |
| `GET /api/alunos`, `GET /api/alunos/{id}` | PROFESSOR (só as turmas dele), DIRETOR, ADMIN |
| `POST /api/alunos` | DIRETOR, ADMIN |
| `GET /api/professores/{id}`, `POST /api/professores` | DIRETOR |

## Catálogo pedagógico

Turmas e matérias não são deste serviço: ficam como ids. O cadastro valida os ids e o
recorte do professor consultam o pedagogico-api (hoje o monólito, `PEDAGOGICO_URL`)
repassando o token da requisição. Se ele estiver fora do ar, o cadastro responde 503 e as
listagens continuam sem o nome da turma. `PEDAGOGICO_ENABLED=false` desliga a integração.

## Testes

`./mvnw verify` — usam H2 em memória (perfil `teste`), sem Postgres nem monólito.

# usuario-api

Microserviço de usuários do Nexo: contas, autenticação (JWT + refresh token em cookie
HttpOnly), perfil, alunos e professores. Porta padrão: **8081**.

## Rodando localmente

```bash
cp .env.example .env        # preencha NEXO_JWT_SECRET e DB_PASSWORD
docker compose up -d        # Postgres do serviço
./mvnw spring-boot:run
```

`NEXO_JWT_SECRET` precisa ser **o mesmo do pedagogico-api (e do monólito)**: é o que faz o token emitido aqui
valer lá. Com `NEXO_SEED_ENABLED=true` (só dev, nunca com o perfil `prod`) nascem as contas
`diretor`, `professor`, `aluno` e `admin`, todas com a senha `123456`.

## Com o front Angular

O `ng serve` usa o `nexo/proxy.conf.js`, que reparte a API por quatro backends (o monólito não é mais usado): login, perfil,
contas, alunos e professores vão para este serviço (8081); turmas, notas, avaliações e
relatórios, para o pedagogico-api (8082); chat, avisos, dúvidas e mensagens, para o
comunicacao-api (8083); dashboard do aluno, desafios e quiz, para o gamificacao-api (8084).
Suba os backends e rode `npm start` em `nexo/`.

## Rotas

| Rota | Quem |
|---|---|
| `POST /api/auth/login`, `/refresh` | público |
| `POST /api/auth/logout`, `GET /api/auth/me` | autenticado |
| `GET/PATCH /api/usuarios/me`, `POST/DELETE /api/usuarios/me/foto`, `POST /api/usuarios/me/senha[/validar]` | autenticado |
| `GET /api/fotos/{id}` | público (id é UUID) |
| `GET /api/admin/contas`, `GET .../resumo`, `PATCH .../{id}/ativo`, `POST .../{id}/senha-provisoria` | ADMIN, DIRETOR |
| `POST /api/admin/contas/sincronizar-contatos` | ADMIN, DIRETOR |
| `GET/PATCH /api/configuracoes[/{secao}]` | autenticado |
| `GET /api/alunos`, `GET /api/alunos/{id}` | PROFESSOR (só as turmas dele), DIRETOR, ADMIN |
| `POST /api/alunos` | DIRETOR, ADMIN |
| `GET /api/professores/{id}`, `POST /api/professores` | DIRETOR |

## Pedagogico-api

Turmas e matérias não são deste serviço: ficam como ids. O cadastro valida os ids e o recorte
do professor consultam o pedagogico-api (`PEDAGOGICO_URL`, padrão `http://localhost:8082`),
repassando o token da requisição. Se ele estiver fora do ar, o cadastro responde 503 e as
listagens continuam sem o nome da turma. `PEDAGOGICO_ENABLED=false` desliga a integração.

O pedagógico guarda uma **cópia enxuta** de aluno e professor (id, nome, turma, foto). Este
serviço a mantém em dia: cadastrar aluno ou professor chama `PUT /api/sync/...` (se falhar, o
cadastro é desfeito) e mudar nome ou foto chama `PATCH /api/sync/usuarios/{id}/perfil` (melhor
esforço). Os **ids dos seeds dos dois serviços precisam bater** — alunos 1 a 12, professores 1
a 5, contas diretor=1, professor=2, aluno=3, admin=4.

## Comunicacao-api

O chat precisa saber quem existe, com que papel, nome e foto, e se o professor ocultou o perfil
dos alunos. Este serviço avisa o comunicacao-api (`COMUNICACAO_URL`, padrão
`http://localhost:8083`) **depois do commit** quando alguém é cadastrado ou muda de nome, foto ou
privacidade. É melhor esforço: o chat fora do ar não desfaz um cadastro. Para se recuperar (ou
para popular um chat novo), `POST /api/admin/contas/sincronizar-contatos` reenvia todas as contas
— esse sim responde 503 se o chat não responder. `COMUNICACAO_ENABLED=false` desliga a integração.

## Gamificacao-api

O ranking precisa conhecer cada aluno (nome, foto) e o que ele escolheu esconder em Configurações
→ Privacidade (exibir no ranking, perfil público). Este serviço avisa o gamificacao-api
(`GAMIFICACAO_URL`, padrão `http://localhost:8084`) **depois do commit**: o cadastro manda o
aluno inteiro (`PUT /api/sync/alunos/{id}`, só gestão); o próprio aluno, ao mudar perfil ou
privacidade, chama `PATCH /api/sync/usuarios/{id}/perfil|privacidade` com o token dele. É melhor
esforço, e `POST /api/admin/contas/sincronizar-jogadores` reenvia todos os alunos (503 se o
serviço não responder). XP, desafios e quiz são de lá e nunca voltam para cá.
`GAMIFICACAO_ENABLED=false` desliga a integração.

## Testes

`./mvnw verify` — usam H2 em memória (perfil `teste`), sem Postgres nem monólito.

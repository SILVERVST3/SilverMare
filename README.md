# SilverMaré — Tábua de marés offline (Android)

App Android de tábuas de maré para a costa brasileira. Funciona **sem internet**:
as constantes harmônicas das estações vêm dentro do app e a maré é calculada no próprio celular,
para qualquer data (passada ou futura).

## O que o app faz

- **GPS**: encontra a estação maregráfica mais próxima (o GPS funciona sem dados móveis) e mostra a distância até ela.
- **Agora**: altura atual, se a maré está enchendo ou vazando, e a próxima preamar e baixa-mar.
- **Previsão por data**: setas ◀ ▶ ou calendário para escolher qualquer dia.
- **Curva do dia**: gráfico com preamares e baixa-mares, faixas de noite e toque para ver a altura em qualquer horário.
- **Tábua do dia**: horários e alturas das preamares e baixa-mares.
- **Sol e Lua**: nascer e pôr do sol, fase da Lua e aviso de sizígia (águas vivas) ou quadratura (águas mortas).
- **Próximos 7 dias**: resumo de cada dia; toque para abrir o dia.
- **Widget na tela inicial** (4×2, redimensionável): altura agora, tendência, próximas preamar e baixa-mar, e a curva das próximas horas.

## Como compilar pelo GitHub Actions

1. Crie um repositório no GitHub e envie todo o conteúdo desta pasta, incluindo a pasta `.github`.
2. O workflow **Build APK** roda sozinho a cada push. Ele também pode ser iniciado em *Actions → Build APK → Run workflow*.
3. Quando terminar, baixe o artefato **SilverMare-apk**. Ele vem em um `.zip` com o `SilverMare.apk` dentro.
4. Instale o APK no celular. Se o Android pedir, permita a instalação de fontes desconhecidas.

O build roda em `ubuntu-latest`, que é o padrão para Android. Ao contrário dos plugins de Windows, aqui não precisa trocar para `windows-2022`.

## Para adicionar o widget

Pressione e segure em um espaço vazio da tela inicial, toque em **Widgets** e procure por **SilverMaré → Maré agora**.
O widget usa a estação escolhida no app e se atualiza a cada 30 minutos. O horário do cálculo aparece no próprio widget.

## Estações incluídas (v1)

São 20 estações da base TICON-4, todas com séries reais de marégrafo:

Arquipélago São Pedro e São Paulo, Santana (AP), Belém (PA), São Luís – Ponta da Madeira (MA), Luís Correia (PI),
Fortaleza – Mucuripe (CE), Fernando de Noronha (PE), Areia Branca – Termisa (RN), Natal (RN), Recife (PE), Suape (PE),
Salvador (BA), Canavieiras (BA), Macaé (RJ), Rio de Janeiro – Ilha Fiscal (RJ), Rio de Janeiro – Leme (RJ),
Ubatuba (SP), Cananéia (SP), Imbituba (SC) e Rio Grande (RS).

Para incluir mais estações, adicione entradas no arquivo `app/src/main/assets/stations.json` no mesmo formato.
As fichas F-41 da Marinha trazem as constantes (H em metros, g em graus); converta cm para m.

## Sobre a precisão

- O motor de cálculo foi conferido com a biblioteca Neaps (referência). Diferença máxima de **0,3 mm** na altura e **1 s** nos horários.
- Nas tábuas de Recife comparadas, os horários ficaram com cerca de 5 a 7 minutos de diferença e a amplitude bateu.
- As alturas são dadas acima do **nível de redução**, aproximado pela média das baixa-mares de sizígia (MLWS).
- A previsão é **astronômica**: vento, pressão e ressacas podem mudar o nível real.
- Não use o app para navegação. Para isso, consulte a tábua oficial da Marinha (DHN/CHM).

## Créditos dos dados

- Constantes harmônicas: TICON-4 (Hart-Davis, M., Dettmering, D., Seitz, F., 2025), CC BY 4.0, https://doi.org/10.17882/109129, obtidas pelo Neaps tide database (MIT).
- Definições das constituintes e correções nodais (IHO): Neaps tide-predictor (MIT).

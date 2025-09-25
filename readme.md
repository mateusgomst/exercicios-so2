
# Exercícios Sistemas Operacionais 2

**Nome do Aluno:** 
Mateus Gomes Teixeira

Este repositório contém a implementação de 10 exercícios práticos sobre concorrência em Java. O objetivo é explorar e aplicar conceitos fundamentais de sistemas operacionais, como gerenciamento de threads, mecanismos de sincronização, prevenção de deadlocks e padrões de comunicação entre processos.

## Como Executar

O projeto é uma aplicação de console. Após compilar o arquivo `ExerciciosConcorrencia.java`, execute a classe principal. Um menu interativo será exibido, permitindo que você escolha qual exercício testar.

```bash
# Compilar o código
javac ExerciciosConcorrencia.java

# Executar e abrir o menu
java ExerciciosConcorrencia
```

-----

## Descrição dos Exercícios

#### 1\. Corrida de Cavalos

Este exercício simula uma corrida onde cada cavalo é uma thread competindo para chegar ao final. O principal desafio é garantir que o vencedor seja declarado de forma justa e sem condições de corrida. A solução utiliza uma flag `volatile` para sinalizar o fim da corrida a todas as threads e um bloco `synchronized` para proteger a seção crítica que define o vencedor e a classificação, garantindo atomicidade.

#### 2\. Buffer Circular (Produtor-Consumidor)

Implementa o clássico problema produtor-consumidor com um buffer de tamanho fixo. Para evitar espera ativa (busy-waiting) e garantir a coordenação, a solução usa um `ReentrantLock` para exclusão mútua e duas variáveis de `Condition` (`notFull` e `notEmpty`). Produtores esperam (`await`) quando o buffer está cheio e consumidores esperam quando está vazio, sendo notificados (`signal`) quando o estado muda.

#### 3\. Transferências Bancárias

Simula um ambiente bancário para ilustrar os perigos de condições de corrida. Uma versão sem travas demonstra como o saldo total do sistema se torna inconsistente. A solução correta protege cada conta com um `ReentrantLock` e, para evitar deadlock durante transferências, implementa uma **ordem global de travamento**, onde o lock da conta de menor ID é sempre adquirido primeiro.

#### 4\. Linha de Processamento (Pipeline)

Modela um pipeline de múltiplos estágios (captura, processamento, gravação), onde cada estágio é uma thread. As threads são conectadas por filas limitadas e sincronizadas. Para um encerramento limpo e sem perda de dados, é utilizado o padrão **"poison pill"**: um objeto de sinalização é inserido no final da fila para indicar a cada estágio que a produção terminou e ele pode finalizar sua execução.

#### 5\. Pool de Threads

Constrói um pool que reutiliza um número fixo de threads para executar tarefas, evitando o custo computacional de criar uma nova thread para cada uma. As tarefas são submetidas a uma `BlockingQueue` (fila concorrente), de onde as threads trabalhadoras retiram e executam os trabalhos. O encerramento do pool é gerenciado de forma segura, garantindo que todas as tarefas na fila sejam concluídas.

#### 6\. Map-Reduce Paralelo

Aplica uma versão simplificada do padrão Map-Reduce para processar um grande conjunto de dados em paralelo. Na fase **Map**, múltiplas threads processam partes independentes dos dados (sem necessidade de locks), gerando resultados parciais. Na fase **Reduce**, a thread principal agrega esses resultados para obter o valor final, medindo o ganho de desempenho (*speedup*) obtido com a paralelização.

#### 7\. O Jantar dos Filósofos

Resolve o clássico problema de deadlock de duas maneiras distintas:

1.  **Ordem Global:** Os filósofos (threads) devem adquirir os garfos (locks) sempre em uma ordem predefinida (o de menor índice primeiro), quebrando a condição de espera circular que causa o deadlock.
2.  **Limitador com Semáforo:** Um `Semaphore` é usado para permitir que no máximo `N-1` filósofos tentem comer ao mesmo tempo. Isso garante que sempre haverá pelo menos um garfo disponível na mesa, evitando o impasse.

#### 8\. Buffer com Rajadas (Backpressure)

Analisa o comportamento do sistema produtor-consumidor sob uma carga de trabalho irregular, com picos (rajadas) de produção. O exercício demonstra o conceito de **backpressure** (contrapressão), onde o buffer limitado, ao ficar cheio, força naturalmente a thread produtora a pausar, estabilizando o sistema sem a necessidade de uma lógica complexa de controle de fluxo.

#### 9\. Corrida de Revezamento

Simula um cenário onde um grupo de threads precisa se sincronizar em um ponto comum antes de prosseguir para a próxima etapa. A solução utiliza um `CyclicBarrier`, que atua como um ponto de encontro. As threads que chegam à barreira ficam bloqueadas até que a última thread do grupo chegue, momento em que todas são liberadas simultaneamente.

#### 10\. Detecção de Deadlock

Este exercício cria propositalmente um cenário de deadlock, onde duas threads tentam adquirir locks em ordens invertidas. Uma terceira thread, a **"watchdog"**, monitora o progresso do sistema. Se nenhuma atividade for detectada por um certo período, ela identifica o deadlock e gera um relatório. A solução definitiva é mostrada ao corrigir o código para usar uma ordem de travamento global.
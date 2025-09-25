import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public class ExerciciosConcorrencia {

    // ===== EXERCÍCIO 1: CORRIDA DE CAVALOS =====
    static class CorridaCavalos {
        private static final int DISTANCIA_TOTAL = 100;
        private static final int NUM_CAVALOS = 5;
        private volatile boolean corridaTerminada = false;
        private final Object lockPlacar = new Object();
        private int vencedor = -1;
        private List<Integer> classificacao = new ArrayList<>();

        class Cavalo implements Runnable {
            private final int id;
            private int posicao = 0;
            private Random random = new Random();

            public Cavalo(int id) {
                this.id = id;
            }

            @Override
            public void run() {
                try {
                    while (!corridaTerminada && posicao < DISTANCIA_TOTAL) {
                        // Passo aleatório entre 1 e 5
                        int passo = random.nextInt(5) + 1;
                        posicao += passo;

                        // Simula tempo de corrida
                        Thread.sleep(100 + random.nextInt(50));

                        System.out.printf("Cavalo %d: posição %d%n", id, Math.min(posicao, DISTANCIA_TOTAL));

                        // Verifica se cruzou a linha de chegada
                        if (posicao >= DISTANCIA_TOTAL) {
                            synchronized (lockPlacar) {
                                if (!corridaTerminada) {
                                    vencedor = id;
                                    corridaTerminada = true;
                                    classificacao.add(id);
                                    System.out.printf("*** CAVALO %d VENCEU A CORRIDA! ***%n", id);
                                } else if (!classificacao.contains(id)) {
                                    classificacao.add(id);
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            public int getId() {
                return id;
            }
        }

        public void iniciarCorrida() throws InterruptedException {
            Scanner scanner = new Scanner(System.in);

            System.out.println("=== CORRIDA DE CAVALOS ===");
            System.out.printf("Cavalos participantes: ");
            for (int i = 1; i <= NUM_CAVALOS; i++) {
                System.out.printf("%d ", i);
            }
            System.out.println();

            System.out.print("Em qual cavalo você quer apostar (1-" + NUM_CAVALOS + ")? ");
            int aposta = scanner.nextInt();

            if (aposta < 1 || aposta > NUM_CAVALOS) {
                System.out.println("Aposta inválida! Apostando no cavalo 1 por padrão.");
                aposta = 1;
            }

            System.out.println("\nPreparando para a largada...");
            Thread.sleep(1000);

            // Cria e inicia as threads dos cavalos
            List<Thread> threads = new ArrayList<>();
            List<Cavalo> cavalos = new ArrayList<>();

            for (int i = 1; i <= NUM_CAVALOS; i++) {
                Cavalo cavalo = new Cavalo(i);
                cavalos.add(cavalo);
                Thread thread = new Thread(cavalo);
                threads.add(thread);
            }

            System.out.println("LARGADA!");

            // Largada sincronizada
            for (Thread thread : threads) {
                thread.start();
            }

            // Aguarda todas as threads terminarem
            for (Thread thread : threads) {
                thread.join();
            }

            // Resultado
            System.out.println("\n=== RESULTADO FINAL ===");
            System.out.printf("Vencedor: Cavalo %d%n", vencedor);
            System.out.printf("Sua aposta: Cavalo %d%n", aposta);

            if (aposta == vencedor) {
                System.out.println("Ganhou a aposta!");
            } else {
                System.out.println("Perdeu a aposta.");
            }

            System.out.println("\nClassificação final:");
            for (int i = 0; i < classificacao.size(); i++) {
                System.out.printf("%dº lugar: Cavalo %d%n", i + 1, classificacao.get(i));
            }
        }

        public static void testarExercicio1() throws InterruptedException {
            new CorridaCavalos().iniciarCorrida();
        }
    }

    // ===== EXERCÍCIO 2: BUFFER CIRCULAR =====
    static class BufferCircular<T> {
        private final T[] buffer;
        private int head = 0;
        private int tail = 0;
        private int count = 0;
        private final int capacity;

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        // Estatísticas
        private volatile long totalProducidos = 0;
        private volatile long totalConsumidos = 0;
        private volatile long tempoEsperaProdutores = 0;
        private volatile long tempoEsperaConsumidores = 0;

        @SuppressWarnings("unchecked")
        public BufferCircular(int capacity) {
            this.capacity = capacity;
            this.buffer = (T[]) new Object[capacity];
        }

        public void put(T item) throws InterruptedException {
            long startTime = System.nanoTime();
            lock.lock();
            try {
                while (count == capacity) {
                    notFull.await();
                }

                buffer[tail] = item;
                tail = (tail + 1) % capacity;
                count++;
                totalProducidos++;

                notEmpty.signal();
            } finally {
                lock.unlock();
                tempoEsperaProdutores += (System.nanoTime() - startTime);
            }
        }

        public T take() throws InterruptedException {
            long startTime = System.nanoTime();
            lock.lock();
            try {
                while (count == 0) {
                    notEmpty.await();
                }

                T item = buffer[head];
                buffer[head] = null;
                head = (head + 1) % capacity;
                count--;
                totalConsumidos++;

                notFull.signal();
                return item;
            } finally {
                lock.unlock();
                tempoEsperaConsumidores += (System.nanoTime() - startTime);
            }
        }

        public long getTotalProducidos() { return totalProducidos; }
        public long getTotalConsumidos() { return totalConsumidos; }
        public double getTempoMedioEsperaProdutores() {
            return totalProducidos > 0 ? tempoEsperaProdutores / (double) totalProducidos / 1_000_000 : 0;
        }
        public double getTempoMedioEsperaConsumidores() {
            return totalConsumidos > 0 ? tempoEsperaConsumidores / (double) totalConsumidos / 1_000_000 : 0;
        }

        static class Produtor implements Runnable {
            private final BufferCircular<Integer> buffer;
            private final int id;
            private final int numItens;
            private final Random random = new Random();

            public Produtor(BufferCircular<Integer> buffer, int id, int numItens) {
                this.buffer = buffer;
                this.id = id;
                this.numItens = numItens;
            }

            @Override
            public void run() {
                try {
                    for (int i = 0; i < numItens; i++) {
                        int item = random.nextInt(1000);
                        buffer.put(item);
                        System.out.printf("Produtor %d produziu: %d%n", id, item);
                        Thread.sleep(random.nextInt(100) + 50);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        static class Consumidor implements Runnable {
            private final BufferCircular<Integer> buffer;
            private final int id;
            private final int numItens;
            private final Random random = new Random();

            public Consumidor(BufferCircular<Integer> buffer, int id, int numItens) {
                this.buffer = buffer;
                this.id = id;
                this.numItens = numItens;
            }

            @Override
            public void run() {
                try {
                    for (int i = 0; i < numItens; i++) {
                        Integer item = buffer.take();
                        System.out.printf("Consumidor %d consumiu: %d%n", id, item);
                        Thread.sleep(random.nextInt(150) + 75);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public static void testarExercicio2() throws InterruptedException {
            System.out.println("=== TESTE DE BUFFER CIRCULAR ===");

            int[] tamanhosBuf = {5, 10, 20, 50};
            int numProdutores = 3;
            int numConsumidores = 2;
            int itensPerThread = 20;

            for (int tamanho : tamanhosBuf) {
                System.out.printf("\n--- Testando buffer de tamanho %d ---%n", tamanho);
                testarBuffer(tamanho, numProdutores, numConsumidores, itensPerThread);
            }
        }

        private static void testarBuffer(int tamanho, int numProd, int numCons, int itensPerThread)
                throws InterruptedException {
            BufferCircular<Integer> buffer = new BufferCircular<>(tamanho);
            List<Thread> threads = new ArrayList<>();

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < numProd; i++) {
                threads.add(new Thread(new Produtor(buffer, i + 1, itensPerThread)));
            }

            for (int i = 0; i < numCons; i++) {
                threads.add(new Thread(new Consumidor(buffer, i + 1,
                        (numProd * itensPerThread) / numCons)));
            }

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                t.join();
            }

            long endTime = System.currentTimeMillis();
            long duracao = endTime - startTime;

            System.out.printf("Tempo total: %d ms%n", duracao);
            System.out.printf("Throughput: %.2f itens/s%n",
                    (buffer.getTotalConsumidos() * 1000.0) / duracao);
            System.out.printf("Tempo médio espera produtores: %.2f ms%n",
                    buffer.getTempoMedioEsperaProdutores());
            System.out.printf("Tempo médio espera consumidores: %.2f ms%n",
                    buffer.getTempoMedioEsperaConsumidores());
        }
    }

    // ===== EXERCÍCIO 3: TRANSFERÊNCIAS BANCÁRIAS =====
    static class ContaBancaria {
        private final int id;
        private volatile double saldo;
        private final ReentrantLock lock = new ReentrantLock();

        public ContaBancaria(int id, double saldoInicial) {
            this.id = id;
            this.saldo = saldoInicial;
        }

        public void depositar(double valor) {
            lock.lock();
            try {
                saldo += valor;
            } finally {
                lock.unlock();
            }
        }

        public boolean sacar(double valor) {
            lock.lock();
            try {
                if (saldo >= valor) {
                    saldo -= valor;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        public double getSaldo() {
            lock.lock();
            try {
                return saldo;
            } finally {
                lock.unlock();
            }
        }

        public int getId() { return id; }
        public ReentrantLock getLock() { return lock; }
    }

    static class TransferenciaBancaria {
        private final List<ContaBancaria> contas;
        private final Random random = new Random();
        private volatile boolean usarTrava;
        private volatile long transferenciasRealizadas = 0;

        public TransferenciaBancaria(int numContas, double saldoInicial, boolean usarTrava) {
            this.usarTrava = usarTrava;
            this.contas = new ArrayList<>();

            for (int i = 0; i < numContas; i++) {
                contas.add(new ContaBancaria(i + 1, saldoInicial));
            }

            System.out.printf("Sistema inicializado com %d contas, saldo inicial: R$ %.2f%n",
                    numContas, saldoInicial);
            System.out.printf("Saldo total inicial: R$ %.2f%n", calcularSaldoTotal());
            System.out.printf("Modo: %s%n", usarTrava ? "COM TRAVA" : "SEM TRAVA");
        }

        public double calcularSaldoTotal() {
            return contas.stream().mapToDouble(ContaBancaria::getSaldo).sum();
        }

        public void transferir(ContaBancaria origem, ContaBancaria destino, double valor) {
            if (origem == destino) return;

            if (usarTrava) {
                transferirComTrava(origem, destino, valor);
            } else {
                transferirSemTrava(origem, destino, valor);
            }
        }

        private void transferirComTrava(ContaBancaria origem, ContaBancaria destino, double valor) {
            ContaBancaria primeiro = origem.getId() < destino.getId() ? origem : destino;
            ContaBancaria segundo = origem.getId() < destino.getId() ? destino : origem;

            primeiro.getLock().lock();
            try {
                segundo.getLock().lock();
                try {
                    if (origem.getSaldo() >= valor) {
                        origem.sacar(valor);
                        destino.depositar(valor);
                        transferenciasRealizadas++;
                        System.out.printf("Transferência: Conta %d -> Conta %d: R$ %.2f%n",
                                origem.getId(), destino.getId(), valor);
                    }
                } finally {
                    segundo.getLock().unlock();
                }
            } finally {
                primeiro.getLock().unlock();
            }
        }

        private void transferirSemTrava(ContaBancaria origem, ContaBancaria destino, double valor) {
            if (origem.getSaldo() >= valor) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                origem.sacar(valor);
                destino.depositar(valor);
                transferenciasRealizadas++;
                System.out.printf("Transferência: Conta %d -> Conta %d: R$ %.2f%n",
                        origem.getId(), destino.getId(), valor);
            }
        }

        class ThreadTransferencia implements Runnable {
            private final int numTransferencias;

            public ThreadTransferencia(int numTransferencias) {
                this.numTransferencias = numTransferencias;
            }

            @Override
            public void run() {
                for (int i = 0; i < numTransferencias; i++) {
                    int origemIdx = random.nextInt(contas.size());
                    int destinoIdx = random.nextInt(contas.size());

                    if (origemIdx != destinoIdx) {
                        double valor = 1 + random.nextDouble() * 99;
                        transferir(contas.get(origemIdx), contas.get(destinoIdx), valor);
                    }

                    try {
                        Thread.sleep(random.nextInt(10) + 5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        public void executarTeste(int numThreads, int transferenciasPerThread) throws InterruptedException {
            System.out.printf("\n=== INICIANDO TESTE ===\n");
            double saldoInicialTotal = calcularSaldoTotal();

            List<Thread> threads = new ArrayList<>();
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < numThreads; i++) {
                Thread thread = new Thread(new ThreadTransferencia(transferenciasPerThread));
                threads.add(thread);
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            long endTime = System.currentTimeMillis();
            double saldoFinalTotal = calcularSaldoTotal();

            System.out.printf("\n=== RESULTADO DO TESTE ===\n");
            System.out.printf("Tempo de execução: %d ms%n", endTime - startTime);
            System.out.printf("Saldo total inicial: R$ %.2f%n", saldoInicialTotal);
            System.out.printf("Saldo total final: R$ %.2f%n", saldoFinalTotal);
            System.out.printf("Diferença: R$ %.2f%n", Math.abs(saldoFinalTotal - saldoInicialTotal));

            if (Math.abs(saldoFinalTotal - saldoInicialTotal) < 0.01) {
                System.out.println("✓ INTEGRIDADE MANTIDA");
            } else {
                System.out.println("✗ ERRO DE INTEGRIDADE!");
            }
        }

        public static void testarExercicio3() throws InterruptedException {
            System.out.println("=== TESTE COM TRAVA ===");
            TransferenciaBancaria sistemaComTrava = new TransferenciaBancaria(5, 1000.0, true);
            sistemaComTrava.executarTeste(10, 50);

            System.out.println("\n\n=== TESTE SEM TRAVA ===");
            TransferenciaBancaria sistemaSemTrava = new TransferenciaBancaria(5, 1000.0, false);
            sistemaSemTrava.executarTeste(10, 50);
        }
    }

    // ===== EXERCÍCIO 4: LINHA DE PROCESSAMENTO =====
    static class Item {
        private final int id;
        private String dados;
        private boolean processado = false;

        public Item(int id, String dados) {
            this.id = id;
            this.dados = dados;
        }

        public int getId() { return id; }
        public String getDados() { return dados; }
        public void setDados(String dados) { this.dados = dados; }
        public boolean isProcessado() { return processado; }
        public void setProcessado(boolean processado) { this.processado = processado; }

        @Override
        public String toString() {
            return String.format("Item{id=%d, dados='%s', processado=%s}",
                    id, dados, processado);
        }
    }

    static class FilaLimitada<T> {
        private final Queue<T> queue = new LinkedList<>();
        private final int capacidade;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();
        private volatile boolean encerrada = false;

        public FilaLimitada(int capacidade) {
            this.capacidade = capacidade;
        }

        public void put(T item) throws InterruptedException {
            lock.lock();
            try {
                while (queue.size() >= capacidade && !encerrada) {
                    notFull.await();
                }
                if (!encerrada) {
                    queue.offer(item);
                    notEmpty.signal();
                }
            } finally {
                lock.unlock();
            }
        }

        public T take() throws InterruptedException {
            lock.lock();
            try {
                while (queue.isEmpty() && !encerrada) {
                    notEmpty.await();
                }
                if (!queue.isEmpty()) {
                    T item = queue.poll();
                    notFull.signal();
                    return item;
                }
                return null;
            } finally {
                lock.unlock();
            }
        }

        public void encerrar() {
            lock.lock();
            try {
                encerrada = true;
                notEmpty.signalAll();
                notFull.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    static class LinhaProcessamento {
        private final FilaLimitada<Item> filaCaptura;
        private final FilaLimitada<Item> filaProcessamento;
        private final List<Item> itensGravados;
        private final int numItens;

        public LinhaProcessamento(int capacidadeFila, int numItens) {
            this.filaCaptura = new FilaLimitada<>(capacidadeFila);
            this.filaProcessamento = new FilaLimitada<>(capacidadeFila);
            this.itensGravados = Collections.synchronizedList(new ArrayList<>());
            this.numItens = numItens;
        }

        class ThreadCaptura implements Runnable {
            @Override
            public void run() {
                System.out.println("Thread Captura iniciada");
                Random random = new Random();

                try {
                    for (int i = 1; i <= numItens; i++) {
                        String dados = "dados_capturados_" + i + "_" + random.nextInt(1000);
                        Item item = new Item(i, dados);

                        filaCaptura.put(item);
                        System.out.printf("Capturado: %s%n", item);
                        Thread.sleep(random.nextInt(50) + 25);
                    }

                    filaCaptura.put(new Item(-1, "END"));
                    System.out.println("Thread Captura: sinal de fim enviado");

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    filaCaptura.encerrar();
                    System.out.println("Thread Captura finalizada");
                }
            }
        }

        class ThreadProcessamento implements Runnable {
            @Override
            public void run() {
                System.out.println("Thread Processamento iniciada");
                Random random = new Random();

                try {
                    while (true) {
                        Item item = filaCaptura.take();
                        if (item == null || item.getId() == -1) {
                            filaProcessamento.put(new Item(-1, "END"));
                            break;
                        }

                        Thread.sleep(random.nextInt(100) + 50);

                        item.setDados("processado_" + item.getDados());
                        item.setProcessado(true);

                        filaProcessamento.put(item);
                        System.out.printf("Processado: %s%n", item);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    filaProcessamento.encerrar();
                    System.out.println("Thread Processamento finalizada");
                }
            }
        }

        class ThreadGravacao implements Runnable {
            @Override
            public void run() {
                System.out.println("Thread Gravação iniciada");
                Random random = new Random();

                try {
                    while (true) {
                        Item item = filaProcessamento.take();
                        if (item == null || item.getId() == -1) {
                            break;
                        }

                        Thread.sleep(random.nextInt(30) + 10);

                        itensGravados.add(item);
                        System.out.printf("Gravado: %s%n", item);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    System.out.println("Thread Gravação finalizada");
                }
            }
        }

        public void executar() throws InterruptedException {
            System.out.printf("=== INICIANDO LINHA DE PROCESSAMENTO ===\n");
            System.out.printf("Processando %d itens...\n\n", numItens);

            Thread threadCaptura = new Thread(new ThreadCaptura());
            Thread threadProcessamento = new Thread(new ThreadProcessamento());
            Thread threadGravacao = new Thread(new ThreadGravacao());

            long startTime = System.currentTimeMillis();

            threadCaptura.start();
            threadProcessamento.start();
            threadGravacao.start();

            threadCaptura.join();
            threadProcessamento.join();
            threadGravacao.join();

            long endTime = System.currentTimeMillis();

            System.out.printf("\n=== RELATÓRIO FINAL ===\n");
            System.out.printf("Tempo total: %d ms%n", endTime - startTime);
            System.out.printf("Itens processados: %d/%d%n", itensGravados.size(), numItens);

            assert itensGravados.size() == numItens : "Perda de itens detectada!";
            System.out.println("✓ Todos os itens foram processados corretamente");
        }

        public static void testarExercicio4() throws InterruptedException {
            new LinhaProcessamento(10, 20).executar();
        }
    }

    // ===== EXERCÍCIO 5: POOL DE THREADS =====
    static class PoolThreads {
        private final int numThreads;
        private final BlockingQueue<Runnable> filaTarefas;
        private final List<Thread> threads;
        private volatile boolean encerrado = false;
        private final AtomicLong tarefasProcessadas = new AtomicLong(0);

        public PoolThreads(int numThreads) {
            this.numThreads = numThreads;
            this.filaTarefas = new LinkedBlockingQueue<>();
            this.threads = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                Thread thread = new Thread(new WorkerThread(i + 1));
                threads.add(thread);
                thread.start();
            }

            System.out.printf("Pool de %d threads iniciado%n", numThreads);
        }

        class WorkerThread implements Runnable {
            private final int id;

            public WorkerThread(int id) {
                this.id = id;
            }

            @Override
            public void run() {
                while (!encerrado || !filaTarefas.isEmpty()) {
                    try {
                        Runnable tarefa = filaTarefas.poll(100, TimeUnit.MILLISECONDS);
                        if (tarefa != null) {
                            System.out.printf("Thread %d executando tarefa%n", id);
                            tarefa.run();
                            tarefasProcessadas.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.printf("Worker Thread %d finalizada%n", id);
            }
        }

        public void adicionarTarefa(Runnable tarefa) {
            if (!encerrado) {
                filaTarefas.offer(tarefa);
            }
        }

        public void encerrar() throws InterruptedException {
            System.out.println("Encerrando pool de threads...");
            encerrado = true;

            for (Thread thread : threads) {
                thread.join();
            }

            System.out.printf("Pool encerrado. Tarefas processadas: %d%n", tarefasProcessadas.get());
        }

        static class TarefaPrimalidade implements Runnable {
            private final int numero;

            public TarefaPrimalidade(int numero) {
                this.numero = numero;
            }

            @Override
            public void run() {
                boolean primo = isPrimo(numero);
                System.out.printf("Número %d é %s%n", numero, primo ? "PRIMO" : "não primo");
            }

            private boolean isPrimo(int n) {
                if (n <= 1) return false;
                if (n <= 3) return true;
                if (n % 2 == 0 || n % 3 == 0) return false;

                for (int i = 5; i * i <= n; i += 6) {
                    if (n % i == 0 || n % (i + 2) == 0) {
                        return false;
                    }
                }
                return true;
            }
        }

        public static void testarExercicio5() throws InterruptedException {
            PoolThreads pool = new PoolThreads(4);

            Random random = new Random();
            for (int i = 0; i < 20; i++) {
                int numero = 1000 + random.nextInt(9000);
                pool.adicionarTarefa(new TarefaPrimalidade(numero));
            }

            Thread.sleep(3000);
            pool.encerrar();
        }
    }

    // ===== EXERCÍCIO 6: MAP-REDUCE PARALELO =====
    static class MapReduceParalelo {

        static class ResultadoLocal {
            long soma = 0;
            Map<Integer, Integer> histograma = new ConcurrentHashMap<>();
        }

        static class WorkerMapReduce implements Runnable {
            private final List<Integer> dados;
            private final int threadId;
            private final ResultadoLocal resultado;

            public WorkerMapReduce(List<Integer> dados, int threadId) {
                this.dados = dados;
                this.threadId = threadId;
                this.resultado = new ResultadoLocal();
            }

            @Override
            public void run() {
                System.out.printf("Thread %d processando %d elementos%n", threadId, dados.size());

                for (int valor : dados) {
                    resultado.soma += valor;
                    resultado.histograma.merge(valor, 1, Integer::sum);
                }

                System.out.printf("Thread %d concluída%n", threadId);
            }

            public ResultadoLocal getResultado() {
                return resultado;
            }
        }

        public static void testarExercicio6() throws InterruptedException {
            int[] numThreads = {1, 2, 4, 8};
            List<Integer> dados = gerarDados(100000);

            System.out.println("=== TESTE MAP-REDUCE PARALELO ===");

            for (int p : numThreads) {
                long startTime = System.currentTimeMillis();

                // Dividir dados em blocos
                List<List<Integer>> blocos = dividirDados(dados, p);
                List<Thread> threads = new ArrayList<>();
                List<WorkerMapReduce> workers = new ArrayList<>();

                // Criar e iniciar threads
                for (int i = 0; i < p; i++) {
                    WorkerMapReduce worker = new WorkerMapReduce(blocos.get(i), i + 1);
                    workers.add(worker);
                    Thread thread = new Thread(worker);
                    threads.add(thread);
                    thread.start();
                }

                // Aguardar conclusão
                for (Thread thread : threads) {
                    thread.join();
                }

                // Fase reduce
                long somaTotal = 0;
                Map<Integer, Integer> histogramaTotal = new HashMap<>();

                for (WorkerMapReduce worker : workers) {
                    somaTotal += worker.getResultado().soma;
                    for (Map.Entry<Integer, Integer> entry : worker.getResultado().histograma.entrySet()) {
                        histogramaTotal.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    }
                }

                long endTime = System.currentTimeMillis();
                long duracao = endTime - startTime;

                System.out.printf("Threads: %d, Tempo: %d ms, Soma: %d, Itens únicos: %d%n",
                        p, duracao, somaTotal, histogramaTotal.size());
            }
        }

        private static List<Integer> gerarDados(int tamanho) {
            List<Integer> dados = new ArrayList<>();
            Random random = new Random(42); // Seed fixo para reprodutibilidade

            for (int i = 0; i < tamanho; i++) {
                dados.add(random.nextInt(1000));
            }

            return dados;
        }

        private static List<List<Integer>> dividirDados(List<Integer> dados, int numBlocos) {
            List<List<Integer>> blocos = new ArrayList<>();
            int tamanhoBloco = dados.size() / numBlocos;
            int resto = dados.size() % numBlocos;

            int inicio = 0;
            for (int i = 0; i < numBlocos; i++) {
                int tamanhoAtual = tamanhoBloco + (i < resto ? 1 : 0);
                blocos.add(dados.subList(inicio, inicio + tamanhoAtual));
                inicio += tamanhoAtual;
            }

            return blocos;
        }
    }

    // ===== EXERCÍCIO 7: FILÓSOFOS =====
    static class FilosofosJantares {
        private final int numFilosofos;
        private final ReentrantLock[] garfos;
        private final AtomicLong[] refeicoes;
        private final AtomicLong[] tempoEspera;
        private final Semaphore limitadorFilosofos;

        public FilosofosJantares(int numFilosofos, boolean usarSemaforo) {
            this.numFilosofos = numFilosofos;
            this.garfos = new ReentrantLock[numFilosofos];
            this.refeicoes = new AtomicLong[numFilosofos];
            this.tempoEspera = new AtomicLong[numFilosofos];
            this.limitadorFilosofos = usarSemaforo ? new Semaphore(numFilosofos - 1) : null;

            for (int i = 0; i < numFilosofos; i++) {
                garfos[i] = new ReentrantLock();
                refeicoes[i] = new AtomicLong(0);
                tempoEspera[i] = new AtomicLong(0);
            }
        }

        class Filosofo implements Runnable {
            private final int id;
            private final Random random = new Random();

            public Filosofo(int id) {
                this.id = id;
            }

            @Override
            public void run() {
                try {
                    for (int i = 0; i < 10; i++) {
                        pensar();
                        comer();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            private void pensar() throws InterruptedException {
                System.out.printf("Filósofo %d pensando...%n", id);
                Thread.sleep(random.nextInt(1000) + 500);
            }

            private void comer() throws InterruptedException {
                long startTime = System.currentTimeMillis();

                if (limitadorFilosofos != null) {
                    limitadorFilosofos.acquire();
                }

                try {
                    // Ordem global para evitar deadlock
                    int garfoEsq = id;
                    int garfoDir = (id + 1) % numFilosofos;

                    if (garfoEsq > garfoDir) {
                        int temp = garfoEsq;
                        garfoEsq = garfoDir;
                        garfoDir = temp;
                    }

                    garfos[garfoEsq].lock();
                    try {
                        garfos[garfoDir].lock();
                        try {
                            long waitTime = System.currentTimeMillis() - startTime;
                            tempoEspera[id].addAndGet(waitTime);

                            System.out.printf("Filósofo %d comendo (refeição %d)...%n",
                                    id, refeicoes[id].get() + 1);
                            Thread.sleep(random.nextInt(500) + 200);
                            refeicoes[id].incrementAndGet();

                        } finally {
                            garfos[garfoDir].unlock();
                        }
                    } finally {
                        garfos[garfoEsq].unlock();
                    }
                } finally {
                    if (limitadorFilosofos != null) {
                        limitadorFilosofos.release();
                    }
                }
            }
        }

        public void iniciarJantar() throws InterruptedException {
            List<Thread> filosofos = new ArrayList<>();

            for (int i = 0; i < numFilosofos; i++) {
                Thread thread = new Thread(new Filosofo(i));
                filosofos.add(thread);
                thread.start();
            }

            for (Thread thread : filosofos) {
                thread.join();
            }

            // Relatório
            System.out.println("\n=== RELATÓRIO FINAL ===");
            for (int i = 0; i < numFilosofos; i++) {
                System.out.printf("Filósofo %d: %d refeições, tempo médio espera: %d ms%n",
                        i, refeicoes[i].get(),
                        refeicoes[i].get() > 0 ? tempoEspera[i].get() / refeicoes[i].get() : 0);
            }
        }

        public static void testarExercicio7() throws InterruptedException {
            System.out.println("=== FILÓSOFOS COM ORDEM GLOBAL ===");
            new FilosofosJantares(5, false).iniciarJantar();

            System.out.println("\n=== FILÓSOFOS COM SEMÁFORO ===");
            new FilosofosJantares(5, true).iniciarJantar();
        }
    }

    // ===== EXERCÍCIO 8: BUFFER COM RAJADAS =====
    static class BufferComRajadas<T> {
        private final T[] buffer;
        private int head = 0;
        private int tail = 0;
        private int count = 0;
        private final int capacity;

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        private final AtomicLong ocupacaoTotal = new AtomicLong(0);
        private final AtomicLong medicoes = new AtomicLong(0);
        private volatile boolean monitorando = true;

        @SuppressWarnings("unchecked")
        public BufferComRajadas(int capacity) {
            this.capacity = capacity;
            this.buffer = (T[]) new Object[capacity];

            // Thread para monitorar ocupação
            new Thread(this::monitorarOcupacao).start();
        }

        public void put(T item) throws InterruptedException {
            lock.lock();
            try {
                while (count == capacity) {
                    notFull.await();
                }

                buffer[tail] = item;
                tail = (tail + 1) % capacity;
                count++;

                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        public T take() throws InterruptedException {
            lock.lock();
            try {
                while (count == 0) {
                    notEmpty.await();
                }

                T item = buffer[head];
                buffer[head] = null;
                head = (head + 1) % capacity;
                count--;

                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }

        private void monitorarOcupacao() {
            while (monitorando) {
                try {
                    ocupacaoTotal.addAndGet(count);
                    medicoes.incrementAndGet();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        public void pararMonitoramento() {
            monitorando = false;
        }

        public double getOcupacaoMedia() {
            long total = medicoes.get();
            return total > 0 ? (double) ocupacaoTotal.get() / total : 0;
        }

        static class ProdutorRajadas implements Runnable {
            private final BufferComRajadas<Integer> buffer;
            private final int id;
            private final Random random = new Random();

            public ProdutorRajadas(BufferComRajadas<Integer> buffer, int id) {
                this.buffer = buffer;
                this.id = id;
            }

            @Override
            public void run() {
                try {
                    for (int ciclo = 0; ciclo < 5; ciclo++) {
                        // Período de rajada
                        System.out.printf("Produtor %d: iniciando rajada %d%n", id, ciclo);
                        for (int i = 0; i < 10; i++) {
                            buffer.put(random.nextInt(1000));
                            Thread.sleep(50);
                        }

                        // Período de ociosidade
                        System.out.printf("Produtor %d: período ocioso%n", id);
                        Thread.sleep(2000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        static class ConsumidorVariavel implements Runnable {
            private final BufferComRajadas<Integer> buffer;
            private final int id;
            private final Random random = new Random();

            public ConsumidorVariavel(BufferComRajadas<Integer> buffer, int id) {
                this.buffer = buffer;
                this.id = id;
            }

            @Override
            public void run() {
                try {
                    for (int i = 0; i < 25; i++) {
                        Integer item = buffer.take();
                        System.out.printf("Consumidor %d consumiu: %d%n", id, item);

                        // Taxa variável de consumo
                        int delay = i % 5 == 0 ? 500 : 100; // Lento a cada 5 itens
                        Thread.sleep(delay);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public static void testarExercicio8() throws InterruptedException {
            System.out.println("=== TESTE BUFFER COM RAJADAS ===");

            BufferComRajadas<Integer> buffer = new BufferComRajadas<>(10);
            List<Thread> threads = new ArrayList<>();

            // Produtores
            for (int i = 0; i < 2; i++) {
                threads.add(new Thread(new ProdutorRajadas(buffer, i + 1)));
            }

            // Consumidores
            for (int i = 0; i < 2; i++) {
                threads.add(new Thread(new ConsumidorVariavel(buffer, i + 1)));
            }

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                t.join();
            }

            Thread.sleep(1000); // Aguardar últimas medições
            buffer.pararMonitoramento();

            System.out.printf("Ocupação média do buffer: %.2f%%\n",
                    (buffer.getOcupacaoMedia() / 10) * 100);
        }
    }

    // ===== EXERCÍCIO 9: CORRIDA DE REVEZAMENTO =====
    static class CorridaRevezamento {
        private final int numEquipes;
        private final int membrosPorEquipe;
        private final CyclicBarrier barreira;
        private final AtomicInteger rodadasConcluidas = new AtomicInteger(0);
        private volatile boolean corridaAtiva = true;

        public CorridaRevezamento(int numEquipes, int membrosPorEquipe) {
            this.numEquipes = numEquipes;
            this.membrosPorEquipe = membrosPorEquipe;
            this.barreira = new CyclicBarrier(numEquipes);
        }

        class Corredor implements Runnable {
            private final int equipe;
            private final int membro;
            private final Random random = new Random();

            public Corredor(int equipe, int membro) {
                this.equipe = equipe;
                this.membro = membro;
            }

            @Override
            public void run() {
                try {
                    while (corridaAtiva) {
                        // Simula corrida
                        System.out.printf("Equipe %d, Membro %d correndo...%n", equipe, membro);
                        Thread.sleep(random.nextInt(1000) + 500);

                        System.out.printf("Equipe %d, Membro %d chegou à barreira%n", equipe, membro);
                        barreira.await();

                        if (membro == 0) { // Apenas um membro por equipe conta rodada
                            int rodada = rodadasConcluidas.incrementAndGet();
                            System.out.printf("*** RODADA %d CONCLUÍDA ***%n", rodada);
                        }

                        Thread.sleep(500); // Pausa entre rodadas
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public void iniciarCorrida(int duracaoSegundos) throws InterruptedException {
            List<Thread> threads = new ArrayList<>();

            for (int equipe = 0; equipe < numEquipes; equipe++) {
                for (int membro = 0; membro < membrosPorEquipe; membro++) {
                    Thread thread = new Thread(new Corredor(equipe + 1, membro));
                    threads.add(thread);
                    thread.start();
                }
            }

            Thread.sleep(duracaoSegundos * 1000);
            corridaAtiva = false;

            for (Thread thread : threads) {
                thread.interrupt();
                thread.join(1000);
            }

            int rodadas = rodadasConcluidas.get();
            double rodadasPorMinuto = (rodadas * 60.0) / duracaoSegundos;

            System.out.printf("\n=== RESULTADO ===\n");
            System.out.printf("Rodadas concluídas: %d em %d segundos%n", rodadas, duracaoSegundos);
            System.out.printf("Taxa: %.2f rodadas por minuto%n", rodadasPorMinuto);
        }

        public static void testarExercicio9() throws InterruptedException {
            int[] tamanhosEquipe = {2, 3, 4, 5};

            for (int tamanho : tamanhosEquipe) {
                System.out.printf("\n=== TESTE COM %d MEMBROS POR EQUIPE ===\n", tamanho);
                CorridaRevezamento corrida = new CorridaRevezamento(3, tamanho);
                corrida.iniciarCorrida(10);
            }
        }
    }

    // ===== EXERCÍCIO 10: DETECÇÃO DE DEADLOCK =====
    static class DeteccaoDeadlock {
        private final ReentrantLock recurso1 = new ReentrantLock();
        private final ReentrantLock recurso2 = new ReentrantLock();
        private final ReentrantLock recurso3 = new ReentrantLock();
        private final AtomicLong ultimaAtividade = new AtomicLong(System.currentTimeMillis());
        private volatile boolean sistemaAtivo = true;
        private final Map<String, Long> statusThreads = new ConcurrentHashMap<>();

        // Thread Watchdog para detectar deadlock
        class WatchdogThread implements Runnable {
            private final int timeoutSegundos;

            public WatchdogThread(int timeoutSegundos) {
                this.timeoutSegundos = timeoutSegundos;
            }

            @Override
            public void run() {
                while (sistemaAtivo) {
                    try {
                        Thread.sleep(1000);

                        long agora = System.currentTimeMillis();
                        long ultimaAtividadeTime = ultimaAtividade.get();

                        if (agora - ultimaAtividadeTime > timeoutSegundos * 1000) {
                            System.out.println("\n*** POSSÍVEL DEADLOCK DETECTADO ***");
                            relatorioDeadlock();
                            break;
                        }

                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            private void relatorioDeadlock() {
                System.out.println("=== RELATÓRIO DE DEADLOCK ===");
                System.out.printf("Última atividade: %d ms atrás%n",
                        System.currentTimeMillis() - ultimaAtividade.get());

                System.out.println("\nStatus dos recursos:");
                System.out.printf("Recurso 1: %s%n", recurso1.isLocked() ? "TRAVADO" : "livre");
                System.out.printf("Recurso 2: %s%n", recurso2.isLocked() ? "TRAVADO" : "livre");
                System.out.printf("Recurso 3: %s%n", recurso3.isLocked() ? "TRAVADO" : "livre");

                System.out.println("\nThreads suspeitas:");
                statusThreads.forEach((nome, timestamp) -> {
                    long tempoEspera = System.currentTimeMillis() - timestamp;
                    if (tempoEspera > 2000) {
                        System.out.printf("- %s: %d ms sem progresso%n", nome, tempoEspera);
                    }
                });
            }
        }

        // Threads que podem causar deadlock
        class ThreadProblematica implements Runnable {
            private final String nome;
            private final boolean ordemCorreta;

            public ThreadProblematica(String nome, boolean ordemCorreta) {
                this.nome = nome;
                this.ordemCorreta = ordemCorreta;
            }

            @Override
            public void run() {
                try {
                    for (int i = 0; i < 5; i++) {
                        statusThreads.put(nome, System.currentTimeMillis());

                        if (ordemCorreta) {
                            operacaoComOrdemCorreta();
                        } else {
                            operacaoComDeadlock();
                        }

                        ultimaAtividade.set(System.currentTimeMillis());
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                statusThreads.remove(nome);
                System.out.printf("Thread %s finalizada%n", nome);
            }

            private void operacaoComDeadlock() throws InterruptedException {
                if (nome.contains("A")) {
                    // Thread A: recurso1 -> recurso2
                    System.out.printf("%s tentando adquirir recurso1%n", nome);
                    recurso1.lock();
                    try {
                        Thread.sleep(100);
                        System.out.printf("%s tentando adquirir recurso2%n", nome);
                        statusThreads.put(nome, System.currentTimeMillis());
                        recurso2.lock();
                        try {
                            System.out.printf("%s executando operação crítica%n", nome);
                            Thread.sleep(200);
                        } finally {
                            recurso2.unlock();
                        }
                    } finally {
                        recurso1.unlock();
                    }
                } else {
                    // Thread B: recurso2 -> recurso1 (ordem diferente - pode causar deadlock)
                    System.out.printf("%s tentando adquirir recurso2%n", nome);
                    recurso2.lock();
                    try {
                        Thread.sleep(100);
                        System.out.printf("%s tentando adquirir recurso1%n", nome);
                        statusThreads.put(nome, System.currentTimeMillis());
                        recurso1.lock();
                        try {
                            System.out.printf("%s executando operação crítica%n", nome);
                            Thread.sleep(200);
                        } finally {
                            recurso1.unlock();
                        }
                    } finally {
                        recurso2.unlock();
                    }
                }
            }

            private void operacaoComOrdemCorreta() throws InterruptedException {
                // Sempre adquire recursos na mesma ordem global
                System.out.printf("%s tentando adquirir recurso1%n", nome);
                recurso1.lock();
                try {
                    Thread.sleep(100);
                    System.out.printf("%s tentando adquirir recurso2%n", nome);
                    recurso2.lock();
                    try {
                        System.out.printf("%s executando operação crítica%n", nome);
                        Thread.sleep(200);
                    } finally {
                        recurso2.unlock();
                    }
                } finally {
                    recurso1.unlock();
                }
            }
        }

        public void testarComDeadlock() throws InterruptedException {
            System.out.println("=== TESTE COM POSSIBILIDADE DE DEADLOCK ===");

            Thread watchdog = new Thread(new WatchdogThread(5));
            Thread threadA = new Thread(new ThreadProblematica("Thread-A", false));
            Thread threadB = new Thread(new ThreadProblematica("Thread-B", false));

            watchdog.start();
            threadA.start();
            threadB.start();

            // Aguarda por 10 segundos ou até deadlock ser detectado
            threadA.join(10000);
            threadB.join(10000);

            sistemaAtivo = false;
            watchdog.interrupt();
            watchdog.join();
        }

        public void testarSemDeadlock() throws InterruptedException {
            System.out.println("\n=== TESTE SEM DEADLOCK (ORDEM GLOBAL) ===");
            sistemaAtivo = true;
            ultimaAtividade.set(System.currentTimeMillis());

            Thread watchdog = new Thread(new WatchdogThread(5));
            Thread threadA = new Thread(new ThreadProblematica("Thread-A-Safe", true));
            Thread threadB = new Thread(new ThreadProblematica("Thread-B-Safe", true));

            watchdog.start();
            threadA.start();
            threadB.start();

            threadA.join();
            threadB.join();

            sistemaAtivo = false;
            watchdog.interrupt();
            watchdog.join();

            System.out.println("✓ Execução completada sem deadlock");
        }

        public static void testarExercicio10() throws InterruptedException {
            DeteccaoDeadlock detector = new DeteccaoDeadlock();

            detector.testarComDeadlock();

            // Reset para segundo teste
            Thread.sleep(2000);

            detector.testarSemDeadlock();
        }
    }

    // ===== MENU PRINCIPAL =====
    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== LABORATÓRIO DE CONCORRÊNCIA ===");
        System.out.println("Escolha um exercício para executar:");
        System.out.println("1. Corrida de Cavalos");
        System.out.println("2. Buffer Circular");
        System.out.println("3. Transferências Bancárias");
        System.out.println("4. Linha de Processamento");
        System.out.println("5. Pool de Threads");
        System.out.println("6. Map-Reduce Paralelo");
        System.out.println("7. Filósofos Jantares");
        System.out.println("8. Buffer com Rajadas");
        System.out.println("9. Corrida de Revezamento");
        System.out.println("10. Detecção de Deadlock");
        System.out.println("0. Sair");

        System.out.print("Digite sua escolha: ");
        int escolha = scanner.nextInt();

        switch (escolha) {
            case 1:
                CorridaCavalos.testarExercicio1();
                break;
            case 2:
                BufferCircular.testarExercicio2();
                break;
            case 3:
                TransferenciaBancaria.testarExercicio3();
                break;
            case 4:
                LinhaProcessamento.testarExercicio4();
                break;
            case 5:
                PoolThreads.testarExercicio5();
                break;
            case 6:
                MapReduceParalelo.testarExercicio6();
                break;
            case 7:
                FilosofosJantares.testarExercicio7();
                break;
            case 8:
                BufferComRajadas.testarExercicio8();
                break;
            case 9:
                CorridaRevezamento.testarExercicio9();
                break;
            case 10:
                DeteccaoDeadlock.testarExercicio10();
                break;
            case 0:
                System.out.println("Saindo...");
                break;
            default:
                System.out.println("Opção inválida!");
        }

        scanner.close();
    }
}
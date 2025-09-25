import java.util.*;
import java.util.concurrent.*;

public class CorridaCavalos {
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
    
    public static void main(String[] args) {
        try {
            new CorridaCavalos().iniciarCorrida();
        } catch (InterruptedException e) {
            System.err.println("Corrida interrompida!");
        }
    }
}
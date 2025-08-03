package main.java;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
//reading the file and creating the solutions (2D array) and fitness function: 15%
//hill-climbing: 15%
//genetic algorithm: 20%
//different “greedy” or “neighbouring” algorithms, evaluation of some parameters of the genetic algorithm, etc.: 15%
//different, more advanced algorithms: ant colony optimisation or exploration of the performance of the algorithms: 15%
//report: 20%

public class ReadInput {
    public Map<String, Object> data; 
    public boolean[][] solution;
    
    public ReadInput() {
        data = new HashMap<String, Object>();
    }

    // Method to enforce cache size constraint
    private void enforceCacheSizeConstraint(boolean[][] solution) {
    int[] videoSizes = (int[]) data.get("video_size_desc");
    int cacheSize = (int) data.get("cache_size");
    
    for (int cache = 0; cache < solution.length; cache++) {
        int contentsSize = 0;
        for (int file = 0; file < solution[cache].length; file++) {
            contentsSize += (solution[cache][file] ? videoSizes[file] : 0);
        }
    
        // If cache size is exceeded, randomly remove videos until constraint is met
        while (contentsSize > cacheSize) {
            int videoToRemove = new Random().nextInt(solution[cache].length);
            if (solution[cache][videoToRemove]) {
                solution[cache][videoToRemove] = false;
                contentsSize -= videoSizes[videoToRemove];
            }
        }
    }
}

    //Create Fitness Function (Section 3)
    public int fitness(boolean[][] solution) {

        //Get video sizes and cache size from data
        int[] videoSizes = (int[]) data.get("video_size_desc");
        int cacheSize = (int) data.get("cache_size");
        
        //Check for violation of the cache limit
        for (int cache = 0; cache < solution.length; cache++) {
            int contentsSize = 0;
            for (int file = 0; file < solution[cache].length; file++) {
                contentsSize += (solution[cache][file] ? videoSizes[file] : 0);
            }
            if (contentsSize > cacheSize) return -1;
        }
        //Get video request data and latency information
        Map<String, String> videoEdRequest = (Map<String, String>) data.get("video_ed_request");
        List<List<Integer>> endpointToCacheLatency = (List<List<Integer>>) data.get("ep_to_cache_latency");
        int totalLatency = 0;
    
        //Calculate total latency for each video request
        for (Map.Entry<String, String> entry : videoEdRequest.entrySet()) {
            String[] parts = entry.getKey().split(",");
            String videoId = parts[0];
            String endpointId = parts[1];
            int requests = Integer.parseInt(entry.getValue());
            int endpointIndex = Integer.parseInt(endpointId);
    
            //Check if endpoint is valid
            if (endpointIndex < 0 || endpointIndex >= endpointToCacheLatency.size()) {
                System.err.println("Invalid edIndex: " + endpointIndex);
                continue; // Skip this request
            }
            
            //Calculate latency to data center  
            int latencyToDC = endpointToCacheLatency.get(endpointIndex).get(0);
            int currentLatency = latencyToDC;
    
            //Find optimal latency to cache for the current video request
            for (int cache = 0; cache < solution[0].length; cache++) {
                if (solution[endpointIndex][cache]) {
                    int latencyToCache = endpointToCacheLatency.get(endpointIndex).get(cache);
                    if (currentLatency > latencyToCache) {
                        currentLatency = latencyToCache;
                    }
                }
            }
            //Add this latency for current video request to total latency
            totalLatency += currentLatency * requests;
        }
        return totalLatency;
    }
    
    
    
// Section 4 (Hill-Climbing Algorithm)
public boolean[][] hillClimbing(ReadInput googleInput) {
    // Initial solution
    boolean[][] solution = new boolean[(int) googleInput.data.get("number_of_videos")][(int) googleInput.data.get("number_of_caches")];
    int currentLatency = fitness(solution); // Initial latency
    int bestLatency = currentLatency;       // Best latency found so far
    int maxConsecutiveNoImprovement = 50;   // Maximum allowed consecutive iterations without improvement
    int consecutiveNoImprovementCount = 0;  // Count of consecutive iterations without improvement

    while (consecutiveNoImprovementCount < maxConsecutiveNoImprovement) {
        boolean[][] bestNeighbor = null;
        int bestNeighborLatency = Integer.MAX_VALUE;
        boolean foundBetterNeighbor = false;

        // Generate and evaluate neighboring solutions
        for (int i = 0; i < (int) googleInput.data.get("number_of_videos"); i++) {
            for (int j = 0; j < (int) googleInput.data.get("number_of_caches"); j++) {
                // Generate neighboring solution by flipping the value at (i, j)
                boolean[][] neighborSolution = generateNeighborSolution(solution, i, j);
                enforceCacheSizeConstraint(neighborSolution);                       //Make sure this neighbour doesn't violate the cache condition
                int neighborLatency = googleInput.fitness(neighborSolution);

                // Check if the neighbor solution is better
                if (neighborLatency < bestNeighborLatency && neighborLatency != -1) {
                    bestNeighbor = neighborSolution;
                    bestNeighborLatency = neighborLatency;
                    foundBetterNeighbor = true;
                }
            }
        }
        // Update the current solution if a better neighbor is found
        if (foundBetterNeighbor) {
            solution = bestNeighbor;
            currentLatency = bestNeighborLatency;
            // Update the best latency if the current solution is better
            if (currentLatency < bestLatency) {
                bestLatency = currentLatency;
                consecutiveNoImprovementCount = 0; // Reset the count
            } else {
                consecutiveNoImprovementCount++; // Increment the count
            }
        } else {
            // No better neighbor found, terminate
            break;
        }
    }
    return solution;
}
// Function to generate a neighboring solution by flipping the value at a specific position
private boolean[][] generateNeighborSolution(boolean[][] currentSolution, int videoIndex, int cacheIndex) {
    int numberOfVideos = currentSolution.length;
    int numberOfCaches = currentSolution[0].length;

    boolean[][] neighborSolution = new boolean[numberOfVideos][numberOfCaches];
    // Copy the current solution to the neighbor solution
    for (int i = 0; i < numberOfVideos; i++) {
        for (int j = 0; j < numberOfCaches; j++) {
            neighborSolution[i][j] = currentSolution[i][j];
        }
    }
    // Flip the value at the specified position
    neighborSolution[videoIndex][cacheIndex] = !neighborSolution[videoIndex][cacheIndex];
    return neighborSolution;
}




//Section 5: Genetic Algorithm
public boolean[][] genetic(ReadInput googleInput) {
    int populationSize = 100;       // Population size 
    int maxGenerations = 1000;      // Maximum number of generations
    double mutationRate = 0.05;     // Mutation rate
    int selectionSize = 5;         //  Size for selection
    Random random = new Random();

    // Generate initial population
    List<boolean[][]> population = new ArrayList<>();
    for (int i = 0; i < populationSize; i++) {
        boolean[][] individual = generateRandomSolution(googleInput);
        population.add(individual);
    }

    for (int generation = 0; generation < maxGenerations; generation++) {
        // Evaluate fitness of each individual in the population
        List<Integer> fitnessValues = new ArrayList<>();
        for (boolean[][] individual : population) {
            int fitness = fitness(individual);
            fitnessValues.add(fitness);
        }

        // Select parents
        List<boolean[][]> parents = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            boolean[][] parent1 = parentSelection(population, fitnessValues, selectionSize, random);
            boolean[][] parent2 = parentSelection(population, fitnessValues, selectionSize, random);
            parents.add(parent1);
            parents.add(parent2);
        }

        // Apply crossover and mutation to create new generation
        population.clear();
        for (int i = 0; i < parents.size(); i += 2) {
            boolean[][] offspring1 = crossover(parents.get(i), parents.get(i + 1), random);
            boolean[][] offspring2 = crossover(parents.get(i + 1), parents.get(i), random);
            population.add(mutate(offspring1, mutationRate, random));
            population.add(mutate(offspring2, mutationRate, random));
        }
    }

    // Get the best solution from the final population
    boolean[][] bestSolution = population.get(0);
    int bestFitness = fitness(bestSolution);
    for (boolean[][] individual : population) {
        int fitness = fitness(individual);
        if (fitness < bestFitness) {
            bestSolution = individual;
            bestFitness = fitness;
        }
    }
    return bestSolution;
}
//Method to generate a random solution
private boolean[][] generateRandomSolution(ReadInput googleInput) {
    int numberOfVideos = (int) googleInput.data.get("number_of_videos");
    int numberOfCaches = (int) googleInput.data.get("number_of_caches");

    Random random = new Random();
    boolean[][] randomSolution = new boolean[numberOfVideos][numberOfCaches];

    for (int i = 0; i < numberOfVideos; i++) {
        for (int j = 0; j < numberOfCaches; j++) {
            randomSolution[i][j] = random.nextBoolean();// Assign a random value (true/false) to indicate whether video i is stored in cache j
        }
    }
    return randomSolution;
}
    
// Parent selection
private boolean[][] parentSelection(List<boolean[][]> population, List<Integer> fitnessValues, int selectionSize, Random random) {
    boolean[][] bestIndividual = null;
    int bestFitness = Integer.MAX_VALUE;
    for (int i = 0; i < selectionSize; i++) {
        int index = random.nextInt(population.size());
        int fitness = fitnessValues.get(index);
        if (fitness < bestFitness) {
            bestFitness = fitness;
            bestIndividual = population.get(index);
        }
    }
    return bestIndividual;
}
// Crossover
private boolean[][] crossover(boolean[][] parent1, boolean[][] parent2, Random random) {
    int numberOfVideos = parent1.length;
    int numberOfCaches = parent1[0].length;
    boolean[][] offspring = new boolean[numberOfVideos][numberOfCaches];

    // Randomly select crossover point
    int crossoverPoint = random.nextInt(numberOfVideos);

    // Copy genetic material from parents to offspring
    for (int i = 0; i < crossoverPoint; i++) {
        offspring[i] = Arrays.copyOf(parent1[i], numberOfCaches);
    }
    for (int i = crossoverPoint; i < numberOfVideos; i++) {
        offspring[i] = Arrays.copyOf(parent2[i], numberOfCaches);
    }
    enforceCacheSizeConstraint(offspring); // Ensure offspring adheres to cache size constraint
    return offspring;
}

//Mutation operator
private boolean[][] mutate(boolean[][] solution, double mutationRate, Random random) {
    int numberOfVideos = solution.length;
    int numberOfCaches = solution[0].length;
    boolean[][] mutatedSolution = new boolean[numberOfVideos][numberOfCaches];

    // Copy genetic material from parent to mutated solution
    for (int i = 0; i < numberOfVideos; i++) {
        mutatedSolution[i] = Arrays.copyOf(solution[i], numberOfCaches);
    }

    // Apply mutation
    for (int i = 0; i < numberOfVideos; i++) {
        for (int j = 0; j < numberOfCaches; j++) {
            if (random.nextDouble() < mutationRate) {
                 mutatedSolution[i][j] = !solution[i][j]; //Flip the value
            }
        }
    }
    enforceCacheSizeConstraint(mutatedSolution);            // Ensure mutated solution adheres to cache size constraint
    return mutatedSolution;
}



    
//Section 6: greedy algorithm
public boolean[][] greedy(ReadInput googleInput) {
    int numberOfVideos = (int) googleInput.data.get("number_of_videos");
    int numberOfCaches = (int) googleInput.data.get("number_of_caches");
    List<List<Integer>> endpointToCacheLatency = (List<List<Integer>>) googleInput.data.get("ep_to_cache_latency");
    Map<String, String> videoEndpointRequest = (Map<String, String>) googleInput.data.get("video_ed_request");

    boolean[][] solution = new boolean[numberOfVideos][numberOfCaches];
    
    for (String videoEndpointKey : videoEndpointRequest.keySet()) {
        String[] parts = videoEndpointKey.split(",");
        String videoId = parts[0];
        String endpointId = parts[1];
        int requests = Integer.parseInt(videoEndpointRequest.get(videoEndpointKey));
        int endpointIndex = Integer.parseInt(endpointId);
    
        if (endpointIndex < 0 || endpointIndex >= endpointToCacheLatency.size()) {
            System.err.println("Invalid edIndex: " + endpointIndex);
            continue; // Skip this request
         }
    
        // Find the cache server with minimum latency for this endpoint
        int minLatency = Integer.MAX_VALUE;
        int bestCache = -1;
        for (int cacheIndex = 0; cacheIndex < numberOfCaches; cacheIndex++) {
            if (cacheIndex < 0 || cacheIndex >= endpointToCacheLatency.get(endpointIndex).size()) {
                System.err.println("Invalid cacheIndex: " + cacheIndex);
                continue; // Skip this cache index
            }
            int latency = endpointToCacheLatency.get(endpointIndex).get(cacheIndex);
            if (latency < minLatency) {
                minLatency = latency;
                bestCache = cacheIndex;
            }
        }
        // Assign the video to the cache server with minimum latency
        if (bestCache != -1) {
            solution[Integer.parseInt(videoId)][bestCache] = true;
        }
    }
    enforceCacheSizeConstraint(solution);
    return solution;
}
    

    public void readGoogle(String filename) throws IOException {
        BufferedReader fin = new BufferedReader(new FileReader(filename));
    
        String system_desc = fin.readLine();
        String[] system_desc_arr = system_desc.split(" ");
        int number_of_videos = Integer.parseInt(system_desc_arr[0]);
        int number_of_endpoints = Integer.parseInt(system_desc_arr[1]);
        int number_of_requests = Integer.parseInt(system_desc_arr[2]);
        int number_of_caches = Integer.parseInt(system_desc_arr[3]);
        int cache_size = Integer.parseInt(system_desc_arr[4]);
    
        Map<String, String> video_ed_request = new HashMap<String, String>();
        String video_size_desc_str = fin.readLine();
        String[] video_size_desc_arr = video_size_desc_str.split(" ");
        int[] video_size_desc = new int[video_size_desc_arr.length];
        for (int i = 0; i < video_size_desc_arr.length; i++) {
            video_size_desc[i] = Integer.parseInt(video_size_desc_arr[i]);
        }
    
        List<List<Integer>> ed_cache_list = new ArrayList<List<Integer>>();
        List<Integer> ep_to_dc_latency = new ArrayList<Integer>();
        List<List<Integer>> ep_to_cache_latency = new ArrayList<List<Integer>>();
        for (int i = 0; i < number_of_endpoints; i++) {
            ep_to_dc_latency.add(0);
            ep_to_cache_latency.add(new ArrayList<Integer>());
    
            String[] endpoint_desc_arr = fin.readLine().split(" ");
            int dc_latency = Integer.parseInt(endpoint_desc_arr[0]);
            int number_of_cache_i = Integer.parseInt(endpoint_desc_arr[1]);
            ep_to_dc_latency.set(i, dc_latency);
    
            for (int j = 0; j < number_of_caches; j++) {
                ep_to_cache_latency.get(i).add(ep_to_dc_latency.get(i) + 1);
            }
    
            List<Integer> cache_list = new ArrayList<Integer>();
            for (int j = 0; j < number_of_cache_i; j++) {
                String[] cache_desc_arr = fin.readLine().split(" ");
                int cache_id = Integer.parseInt(cache_desc_arr[0]);
                int latency = Integer.parseInt(cache_desc_arr[1]);
                cache_list.add(cache_id);
                ep_to_cache_latency.get(i).set(cache_id, latency);
            }
            ed_cache_list.add(cache_list);
        }
    
        for (int i = 0; i < number_of_requests; i++) {
            String[] request_desc_arr = fin.readLine().split(" ");
            String video_id = request_desc_arr[0];
            String ed_id = request_desc_arr[1];
            String requests = request_desc_arr[2];
            video_ed_request.put(video_id + "," + ed_id, requests);
        }
    
        data.put("number_of_videos", number_of_videos);
        data.put("number_of_endpoints", number_of_endpoints);
        data.put("number_of_requests", number_of_requests);
        data.put("number_of_caches", number_of_caches);
        data.put("cache_size", cache_size);
        data.put("video_size_desc", video_size_desc);
        data.put("ep_to_dc_latency", ep_to_dc_latency);
        data.put("ep_to_cache_latency", ep_to_cache_latency);
        data.put("ed_cache_list", ed_cache_list);
        data.put("video_ed_request", video_ed_request);

        fin.close();
     }

     public String toString() {
        String result = "";

        //for each endpoint: 
        for(int i = 0; i < (Integer) data.get("number_of_endpoints"); i++) {
            result += "enpoint number " + i + "\n";
            //latendcy to DC
            int latency_dc = ((List<Integer>) data.get("ep_to_dc_latency")).get(i);
            result += "latency to dc " + latency_dc + "\n";
            //for each cache
            for(int j = 0; j < ((List<List<Integer>>) data.get("ep_to_cache_latency")).get(i).size(); j++) {
                int latency_c = ((List<List<Integer>>) data.get("ep_to_cache_latency")).get(i).get(j); 
                result += "latency to cache number " + j + " = " + latency_c + "\n";
            }
        }
        return result;
    }

    public static void main(String[] args) throws IOException {  
        ReadInput ri = new ReadInput();
        ri.readGoogle("C:\\Users\\lucia\\Downloads\\Algorithms\\project-optimisation-algorithms-luciabrown\\input\\me_at_the_zoo.in");

        //Create Solution Array (Section 2)
        boolean [][] solution = new boolean [(int)ri.data.get("number_of_videos")][(int)ri.data.get("number_of_caches")];
    
        /*Test Case To Check That fitness() is Working */
      //  Stopwatch stopwatchFitness = Stopwatch.createStarted();
        int fitnessScore = ri.fitness(solution);
   //     stopwatchFitness.stop();
        System.out.println("Fitness Score: " + fitnessScore);
    //    System.out.println("Time taken by fitness(): " + stopwatchFitness.elapsed(TimeUnit.MILLISECONDS) + " milliseconds");
        
        /*  Test Case To Check That hillClimbing() is Working*/
    //    Stopwatch stopwatchHillClimbing = Stopwatch.createStarted();
        boolean[][] optimizedSolutionHillClimbing = ri.hillClimbing(ri);
        int optimizedFitnessScoreHillClimbing = ri.fitness(optimizedSolutionHillClimbing);
   //     stopwatchHillClimbing.stop();
        System.out.println("Fitness Score after hill-climbing: " + optimizedFitnessScoreHillClimbing);
        System.out.println("Change in latency after hill-climbing: " + (fitnessScore - optimizedFitnessScoreHillClimbing));
  //      System.out.println("Time taken by hillClimbing(): " + stopwatchHillClimbing.elapsed(TimeUnit.MILLISECONDS) + " milliseconds");

        /*Test Case To Check That greedy() is Working*/
  //      Stopwatch stopwatchGreedy = Stopwatch.createStarted();
        boolean[][] optimizedSolutionGreedy = ri.greedy(ri);
        int optimizedFitnessScoreGreedy = ri.fitness(optimizedSolutionGreedy);
 //       stopwatchGreedy.stop();
        System.out.println("Fitness Score after Greedy: " + optimizedFitnessScoreGreedy);
        System.out.println("Change in latency after Greedy: " + (fitnessScore - optimizedFitnessScoreGreedy));
   //     System.out.println("Time taken by greedy(): " + stopwatchGreedy.elapsed(TimeUnit.MILLISECONDS) + " milliseconds");

        /* Test Case To Check That genetic() is Working*/
   //     Stopwatch stopwatchGenetic = Stopwatch.createStarted();
        boolean[][] optimizedSolutionGenetic = ri.genetic(ri);
        int optimizedFitnessScoreGenetic = ri.fitness(optimizedSolutionGenetic);
   //     stopwatchGenetic.stop();
        System.out.println("Fitness Score after Genetic: " + optimizedFitnessScoreGenetic);
        System.out.println("Change in latency after Genetic: " + (fitnessScore - optimizedFitnessScoreGenetic));
   //     System.out.println("Time taken by genetic(): " + stopwatchGenetic.elapsed(TimeUnit.MILLISECONDS) + " milliseconds");
    }
}

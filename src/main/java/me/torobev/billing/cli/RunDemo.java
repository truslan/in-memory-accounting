package me.torobev.billing.cli;

import com.beust.jcommander.Parameter;
import com.google.common.base.Stopwatch;
import me.torobev.billing.Account;
import me.torobev.billing.Transfer;
import me.torobev.billing.rest.RestClient;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Stopwatch.createStarted;
import static java.lang.Runtime.getRuntime;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.ThreadLocalRandom.current;
import static java.util.concurrent.TimeUnit.*;
import static java.util.stream.IntStream.rangeClosed;

public class RunDemo implements Runnable {


	@Parameter(names = {"-u", "--url"}, description = "Server url")
	private String url = "http://localhost:8080/";

	@Parameter(names = {"-c", "--concurrency"}, description = "Number of concurrent requests")
	private int concurency = getRuntime().availableProcessors();

	@Parameter(names = {"-n", "--number"}, description = "Number of total transfer requests")
	private int transfers = 1000;

	@Parameter(names = {"-a", "--accounts"}, description = "Number of total created accounts")
	private int accounts = 100;


	@Override
	public void run() {
		ThreadPoolExecutor pool = new ThreadPoolExecutor(concurency, concurency,
			1, MINUTES, new ArrayBlockingQueue<>(1000), new ThreadPoolExecutor.CallerRunsPolicy());

		RestClient accounting = new RestClient(url);

		Stopwatch sw = createStarted();
		rangeClosed(1, accounts)
			.mapToObj(i -> requireNonNull(accounting.createAccount()))
			.map(Account::getId)
			.forEach(id -> checkState(accounting.increaseAccountBalance(id, 1000000L)));

		long elapsed = sw.elapsed(MILLISECONDS);
		float rps = 1000f * accounts / elapsed;
		System.out.format("Created %d accounts in %d ms, %.1f rps\n", accounts, elapsed, rps);


		sw.reset().start();
		for (int i = 0; i < transfers; i++) {
			int from = current().nextInt(1, transfers + 1);
			int to = current().nextInt(1, transfers + 1);
			pool.submit(() -> accounting.execute(new Transfer(from, to, 1L)));
		}
		pool.shutdown();
		try {
			pool.awaitTermination(10, SECONDS);
		} catch (InterruptedException e) {
			currentThread().interrupt();
			return;
		}

		elapsed = sw.elapsed(MILLISECONDS);
		rps = 1000f * transfers / elapsed;
		System.out.format("Executed %d transfers in %d ms, %.1f rps\n", transfers, elapsed, rps);
	}
}

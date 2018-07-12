package me.torobev.billing.rest;

import com.google.common.base.Stopwatch;
import me.torobev.billing.Account;
import me.torobev.billing.Transfer;
import me.torobev.billing.web.WebServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.ServerSocket;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Stopwatch.createStarted;
import static com.google.common.io.Closeables.close;
import static java.lang.Runtime.getRuntime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.ThreadLocalRandom.current;
import static java.util.concurrent.TimeUnit.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.rangeClosed;
import static me.torobev.billing.accounting.Accounting.TransferResult.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;

public class RestClientTest {

	private WebServer server;
	private RestClient accounting;
	private ExecutorService pool;

	@BeforeMethod
	public void setUp() throws Exception {
		ServerSocket socket = new ServerSocket(0);
		int port = socket.getLocalPort();
		socket.close();

		server = new WebServer(port);
		server.start();
		accounting = new RestClient("http://localhost:" + port);
		pool =
			new ThreadPoolExecutor(getRuntime().availableProcessors(), getRuntime().availableProcessors(),
				1, MINUTES, new ArrayBlockingQueue<>(1000), new CallerRunsPolicy());
	}

	@AfterMethod
	public void tearDown() throws Exception {
		close(accounting, true);
		server.stop();
	}

	@Test
	public void commonScenario() {
		Account account = accounting.createAccount();
		assertThat(account.getBalance(), is(0L));
		assertThat(account.getId(), greaterThan(0));

		Account account2 = accounting.createAccount();
		assertThat(account2.getId(), greaterThan(0));
		assertThat(account2.getId(), not(account.getId()));

		assertThat(accounting.getAccount(account.getId()), is(account));
		assertThat(accounting.getAccount(account2.getId()), is(account2));

		assertThat(accounting.getAccountLog(account.getId()), empty());
		assertThat(accounting.getAccountLog(account2.getId()), empty());

		assertThat(accounting.increaseAccountBalance(account.getId(), 10L), is(true));
		assertThat(accounting.increaseAccountBalance(account2.getId(), 20L), is(true));

		assertThat(accounting.getAccount(account.getId()).getBalance(), is(10L));
		assertThat(accounting.getAccount(account2.getId()).getBalance(), is(20L));

		assertThat(accounting.getAccountLog(account.getId()), hasItems(new Transfer(0, account.getId(), 10L)));
		assertThat(accounting.getAccountLog(account2.getId()), hasItems(new Transfer(0, account2.getId(), 20L)));

		assertThat(accounting.execute(new Transfer(account.getId(), account2.getId(), 100)), is(SOURCE_BALANCE_CHECK_FAILED));
		assertThat(accounting.execute(new Transfer(account.getId(), account2.getId(), 5)), is(OK));
		assertThat(accounting.execute(new Transfer(account.getId(), account2.getId(), 1)), is(OK));
		assertThat(accounting.execute(new Transfer(account.getId(), account2.getId(), 4)), is(OK));
		assertThat(accounting.execute(new Transfer(account.getId(), account2.getId(), 1)), is(SOURCE_BALANCE_CHECK_FAILED));

		assertThat(accounting.getAccount(account.getId()).getBalance(), is(0L));
		assertThat(accounting.getAccount(account2.getId()).getBalance(), is(30L));

		assertThat(accounting.getAccountLog(account.getId()), hasItems(
			new Transfer(0, account.getId(), 10L),
			new Transfer(account.getId(), account2.getId(), 5),
			new Transfer(account.getId(), account2.getId(), 1),
			new Transfer(account.getId(), account2.getId(), 4)
		));

		assertThat(accounting.getAccountLog(account2.getId()), hasItems(
			new Transfer(0, account2.getId(), 20L),
			new Transfer(account.getId(), account2.getId(), 5),
			new Transfer(account.getId(), account2.getId(), 1),
			new Transfer(account.getId(), account2.getId(), 4)
		));

		assertThat(accounting.removeAccount(account.getId()), is(true));

		assertThat(accounting.execute(new Transfer(account.getId(), account2.getId(), 4)), is(SOURCE_NOT_FOUND));
		assertThat(accounting.execute(new Transfer(account2.getId(), account.getId(), 4)), is(DESTINATION_NOT_FOUND));

		assertThat(accounting.getAccount(account.getId()), nullValue());
		assertThat(accounting.getAccountLog(account.getId()), empty());
	}

	@Test(invocationCount = 10)
	public void underLoad() throws InterruptedException {
		int count = 100;
		Stopwatch sw = createStarted();
		List<Integer> ids = rangeClosed(1, count)
			.mapToObj(i -> requireNonNull(accounting.createAccount()))
			.map(Account::getId)
			.peek(id -> checkState(accounting.increaseAccountBalance(id, 1000000L)))
			.collect(toList());

		long elapsed = sw.elapsed(MILLISECONDS);
		float rps = 1000f * count / elapsed;
		System.out.format("Created %d accounts in %d ms, %.1f rps\n", count, elapsed, rps);

		long sum = ids.stream().map(id -> accounting.getAccount(id)).filter(Objects::nonNull).mapToLong(Account::getBalance).sum();
		assertThat("initial sum check", sum, is(1000000L * count));


		sw.reset().start();
		int transfers = 10000;
		for (int i = 0; i < transfers; i++) {
			int from = current().nextInt(1, count + 1);
			int to = current().nextInt(1, count + 1);
			pool.submit(() -> accounting.execute(new Transfer(from, to, 1L)));
		}
		pool.shutdown();
		pool.awaitTermination(10, SECONDS);

		elapsed = sw.elapsed(MILLISECONDS);
		rps = 1000f * transfers / elapsed;
		System.out.format("Executed %d transfers in %d ms, %.1f rps\n", transfers, elapsed, rps);



		sum = ids.stream().map(id -> accounting.getAccount(id)).mapToLong(Account::getBalance).sum();
		assertThat("initial sum check", sum, is(1000000L * count));
	}
}
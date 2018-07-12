package me.torobev.billing.accounting;


import com.google.common.base.Stopwatch;
import me.torobev.billing.Account;
import me.torobev.billing.Transfer;
import org.hamcrest.CoreMatchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Stopwatch.createStarted;
import static java.lang.Runtime.getRuntime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.ThreadLocalRandom.current;
import static java.util.concurrent.TimeUnit.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.rangeClosed;
import static me.torobev.billing.accounting.Accounting.TransferResult.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class InMemoryAccountingTest {


	private ExecutorService pool;
	private InMemoryAccounting accounting;

	@BeforeMethod
	public void setUp() {
		this.accounting = new InMemoryAccounting();
		pool =
			new ThreadPoolExecutor(getRuntime().availableProcessors(), getRuntime().availableProcessors(),
				1, MINUTES, new ArrayBlockingQueue<>(1000), new ThreadPoolExecutor.CallerRunsPolicy());
	}

	@Test
	public void commonScenario() {
		Account acc1 = accounting.createAccount();
		Account acc2 = accounting.createAccount();

		assertThat(accounting.getAccountLog(acc1.getId()), empty());
		assertThat(accounting.getAccountLog(acc2.getId()), empty());

		accounting.increaseAccountBalance(acc1.getId(), 20L);
		accounting.increaseAccountBalance(acc2.getId(), 30L);

		assertThat(accounting.getAccountLog(acc1.getId()), CoreMatchers.hasItem(new Transfer(0, acc1.getId(), 20L)));
		assertThat(accounting.getAccountLog(acc2.getId()), hasItem(new Transfer(0, acc2.getId(), 30L)));


		assertThat(accounting.getAccount(acc1.getId()).getBalance(), is(20L));
		assertThat(accounting.getAccount(acc2.getId()).getBalance(), is(30L));

		assertThat(accounting.execute(new Transfer(acc1.getId(), acc2.getId(), 10L)), is(OK));

		assertThat(accounting.getAccount(acc1.getId()).getBalance(), is(10L));
		assertThat(accounting.getAccount(acc2.getId()).getBalance(), is(40L));

		assertThat(accounting.execute(new Transfer(acc1.getId(), acc2.getId(), 5L)), is(OK));

		assertThat(accounting.getAccount(acc1.getId()).getBalance(), is(5L));
		assertThat(accounting.getAccount(acc2.getId()).getBalance(), is(45L));

		assertThat(accounting.execute(new Transfer(acc1.getId(), acc2.getId(), 10L)), is(SOURCE_BALANCE_CHECK_FAILED));

		assertThat(accounting.getAccount(acc1.getId()).getBalance(), is(5L));
		assertThat(accounting.getAccount(acc2.getId()).getBalance(), is(45L));

		assertThat(accounting.execute(new Transfer(acc1.getId(), acc2.getId(), 5L)), is(OK));

		assertThat(accounting.getAccount(acc1.getId()).getBalance(), is(0L));
		assertThat(accounting.getAccount(acc2.getId()).getBalance(), is(50L));

		assertThat(accounting.execute(new Transfer(acc2.getId(), acc1.getId(), 42L)), is(OK));

		assertThat(accounting.getAccount(acc1.getId()).getBalance(), is(42L));
		assertThat(accounting.getAccount(acc2.getId()).getBalance(), is(8L));


		assertThat(accounting.getAccountLog(acc1.getId()), hasItems(
			new Transfer(0, acc1.getId(), 20L),
			new Transfer(acc1.getId(), acc2.getId(), 10L),
			new Transfer(acc1.getId(), acc2.getId(), 5L),
			new Transfer(acc1.getId(), acc2.getId(), 5L),
			new Transfer(acc2.getId(), acc1.getId(), 42L)
		));

		assertThat(accounting.getAccountLog(acc2.getId()), hasItems(
			new Transfer(0, acc2.getId(), 30L),
			new Transfer(acc1.getId(), acc2.getId(), 10L),
			new Transfer(acc1.getId(), acc2.getId(), 5L),
			new Transfer(acc1.getId(), acc2.getId(), 5L),
			new Transfer(acc2.getId(), acc1.getId(), 42L)
		));

		assertThat(accounting.removeAccount(acc1.getId()), is(true));
		assertThat(accounting.getAccount(acc1.getId()), is(nullValue()));
		assertThat(accounting.getAccountLog(acc1.getId()), empty());

		assertThat(accounting.increaseAccountBalance(acc1.getId(), 1), is(false));
		assertThat(accounting.execute(new Transfer(acc1.getId(), acc2.getId(), 1L)), is(SOURCE_NOT_FOUND));
		assertThat(accounting.execute(new Transfer(acc2.getId(), acc1.getId(), 1L)), is(DESTINATION_NOT_FOUND));
	}

	@Test(invocationCount = 30)
	public void underLoad() throws InterruptedException {
		int count = 10000;
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
		int transfers = 100000;
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
package me.torobev.billing.accounting;

import me.torobev.billing.Account;
import me.torobev.billing.Transfer;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * In memory almost lock-free implementation of {@link Accounting}.
 * {@link #createAccount()} is blocking, but we assume that this method will not be used frequently.
 */
public class InMemoryAccounting implements Accounting {

	private final AtomicInteger nextId = new AtomicInteger(0);
	private static final long MAX_ID = 100000;

	private final Map<Integer, AccountHolder> accounts = new ConcurrentHashMap<>();
	private final Queue<Transfer> transferLog = new ConcurrentLinkedQueue<>();

	private static class AccountHolder {

		private final AtomicReference<Account> account;
		private volatile boolean deleted;

		AccountHolder(Account account, boolean deleted) {
			this.account = new AtomicReference<>(account);
			this.deleted = deleted;
		}

		void markDeleted() {
			this.deleted = true;
		}
	}

	@Override
	public Account createAccount() {
		int id = nextId.incrementAndGet();
		checkState(id <= MAX_ID, "Storage size limit reached");

		Account account = new Account(id, 0L);
		accounts.put(id, new AccountHolder(account, false));
		return account;
	}

	@Override
	public boolean removeAccount(int id) {
		AccountHolder holder = accounts.get(id);
		if (holder == null) {
			return false;
		}
		Account account = borrowAccount(holder);
		holder.markDeleted();
		holder.account.set(account);

		return true;
	}

	@Override
	public Account getAccount(int id) {
		AccountHolder holder = accounts.get(id);
		if (holder == null || holder.deleted) {
			return null;
		}
		Account read;
		do {
			read = holder.account.get();
		} while (read == null);
		return read;
	}

	@Override
	public boolean increaseAccountBalance(int id, long amount) {
		checkArgument(amount > 0L, "Positive value required.");
		AccountHolder holder = accounts.get(id);
		if (holder == null || holder.deleted) {
			return false;
		}
		Account account = borrowAccount(holder);

		try {
			account = new Account(account.getId(), account.getBalance() + amount);
			Transfer e = new Transfer(0, account.getId(), amount);
			while (!transferLog.offer(e)) ;
		} finally {
			// return account to holder
			holder.account.set(account);
		}

		return true;
	}

	private Account borrowAccount(AccountHolder holder) {
		// busy waiting account borrow
		while (true) {
			Account account = holder.account.get();
			if (account != null && holder.account.compareAndSet(account, null)) {
				return account;
			}
		}
	}

	@Override
	public TransferResult execute(Transfer transfer) {
		TransferResult result = transfer(transfer.getSrcId(), transfer.getDstId(), transfer.getAmount());
		if (result == TransferResult.OK) {

		}
		return result;
	}

	private TransferResult transfer(int srcId, int dstId, long amount) {
		if (srcId == dstId) {
			return TransferResult.SAME_ACCOUNTS;
		}
		if (amount <= 0L) {
			return TransferResult.AMOUNT_CHECK_FAILED;
		}

		AccountHolder srcHolder = accounts.get(srcId);
		if (srcHolder == null) {
			return TransferResult.SOURCE_NOT_FOUND;
		}

		AccountHolder dstHolder = accounts.get(dstId);
		if (dstHolder == null) {
			return TransferResult.DESTINATION_NOT_FOUND;
		}

		Account srcAccount;
		Account dstAccount;
		// we need to borrow accounts in same order regardless direction of the transfer to prevent deadlock
		if (srcId < dstId) {
			srcAccount = borrowAccount(srcHolder);
			dstAccount = borrowAccount(dstHolder);
		} else {
			dstAccount = borrowAccount(dstHolder);
			srcAccount = borrowAccount(srcHolder);
		}

		try {
			if (srcHolder.deleted) {
				return TransferResult.SOURCE_NOT_FOUND;
			}
			if (dstHolder.deleted) {
				return TransferResult.DESTINATION_NOT_FOUND;
			}

			long left = srcAccount.getBalance() - amount;
			if (left < 0L) {
				return TransferResult.SOURCE_BALANCE_CHECK_FAILED;
			}
			srcAccount = new Account(srcAccount.getId(), left);
			dstAccount = new Account(dstAccount.getId(), dstAccount.getBalance() + amount);

			Transfer e = new Transfer(srcAccount.getId(), dstAccount.getId(), amount);
			while (!transferLog.offer(e)) ;

		} finally {
			dstHolder.account.set(dstAccount);
			srcHolder.account.set(srcAccount);
		}
		return TransferResult.OK;
	}

	@Override
	public List<Transfer> getAccountLog(int id) {
		AccountHolder holder = accounts.get(id);
		if (holder == null || holder.deleted) {
			return emptyList();
		}

		Account account = borrowAccount(holder);
		try {
			return transferLog.stream()
				.filter(t -> t.getDstId() == id || t.getSrcId() == id)
				.collect(toList());
		} finally {
			holder.account.set(account);
		}
	}
}

package me.torobev.billing.accounting;

import me.torobev.billing.Account;
import me.torobev.billing.Transfer;

import java.util.List;

public interface Accounting {

	enum TransferResult {
		OK,
		SOURCE_NOT_FOUND,
		DESTINATION_NOT_FOUND,
		SOURCE_BALANCE_CHECK_FAILED,
		SAME_ACCOUNTS,
		AMOUNT_CHECK_FAILED,
		ERROR
	}

	/**
	 * Creates and returns brand new account
	 *
	 * @return created account
	 */
	Account createAccount();

	/**
	 * @param id account to remove
	 * @return {@code true} if account successfully removed, {@code false} otherwise
	 */
	boolean removeAccount(int id);

	/**
	 * @param id account identifier
	 * @return current account stater, {@code null} when account not found
	 */
	Account getAccount(int id);

	/**
	 * increases account balance by amount provided
	 *
	 * @param id     account identifier
	 * @param amount amount in cents to add to balance
	 * @return {@code true} on success, {@code false} when account not found
	 */
	boolean increaseAccountBalance(int id, long amount);

	/**
	 * Executes provided money transfer between two accounts
	 *
	 * @param transfer desired transfer
	 * @return result of transfer
	 * @see TransferResult
	 */
	TransferResult execute(Transfer transfer);

	/**
	 * @param id account identifier
	 * @return list of transfers related to given account
	 */
	List<Transfer> getAccountLog(int id);

}

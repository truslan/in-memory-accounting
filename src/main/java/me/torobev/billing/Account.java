package me.torobev.billing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Account state
 */
public class Account {

	@JsonProperty(value = "id")
	private final int id;

	@JsonProperty(value = "balance")
	private final long balance;

	@JsonCreator
	public Account(@JsonProperty(value = "id") int id, @JsonProperty(value = "amount") long amount) {
		checkArgument(id > 0);
		checkArgument(amount >= 0L);
		this.id = id;
		this.balance = amount;
	}

	public int getId() {
		return id;
	}

	public long getBalance() {
		return balance;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Account)) return false;

		Account account = (Account) o;

		if (id != account.id) return false;
		return balance == account.balance;
	}

	@Override
	public int hashCode() {
		int result = id;
		result = 31 * result + (int) (balance ^ (balance >>> 32));
		return result;
	}

	@Override
	public String toString() {
		return "Account{" +
			"id=" + id +
			", balance=" + balance +
			'}';
	}
}

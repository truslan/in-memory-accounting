package me.torobev.billing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.google.common.base.Preconditions.*;

/**
 * Transfer between two billing accounts. External income has {@link #srcId} == 0.
 */
public class Transfer {

	@JsonProperty(value = "srcId")
	private final int srcId;
	@JsonProperty(value = "dstId")
	private final int dstId;
	@JsonProperty(value = "amount")
	private final long amount;

	/**
	 * @param srcId  source account id. 0 for external.
	 * @param dstId  destination account id.
	 * @param amount amount of cents to transfer.
	 */
	@JsonCreator
	public Transfer(@JsonProperty(value = "srcId") int srcId,
									@JsonProperty(value = "dstId") int dstId,
									@JsonProperty(value = "amount") long amount
	) {
		checkArgument(srcId >= 0);
		checkArgument(dstId > 0);
		checkArgument(amount > 0);

		this.srcId = srcId;
		this.dstId = dstId;
		this.amount = amount;
	}

	public int getSrcId() {
		return srcId;
	}

	public int getDstId() {
		return dstId;
	}

	public long getAmount() {
		return amount;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Transfer)) return false;

		Transfer transfer = (Transfer) o;

		if (srcId != transfer.srcId) return false;
		if (dstId != transfer.dstId) return false;
		return amount == transfer.amount;
	}

	@Override
	public int hashCode() {
		int result = srcId;
		result = 31 * result + dstId;
		result = 31 * result + (int) (amount ^ (amount >>> 32));
		return result;
	}

	@Override
	public String toString() {
		return "Transfer{" +
			"srcId=" + srcId +
			", dstId=" + dstId +
			", amount=" + amount +
			'}';
	}
}

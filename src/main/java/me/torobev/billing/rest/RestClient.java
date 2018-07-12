package me.torobev.billing.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.torobev.billing.Account;
import me.torobev.billing.Transfer;
import me.torobev.billing.accounting.Accounting;
import me.torobev.billing.web.Handler.Result;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.emptyList;
import static javax.servlet.http.HttpServletResponse.*;

/**
 * {@link Accounting} REST API client
 */
public class RestClient implements Accounting, Closeable {

	private final URI base;
	private final CloseableHttpClient httpClient;


	private final ObjectMapper mapper = new ObjectMapper();

	private static final TypeReference<Result<Account>> ACCOUNT_RESULT =
		new TypeReference<Result<Account>>() {
		};

	private static final TypeReference<Result<List<Transfer>>> ACCOUNT_LOG_RESULT =
		new TypeReference<Result<List<Transfer>>>() {
		};

	private static final TypeReference<Result<String>> TEXT_RESULT =
		new TypeReference<Result<String>>() {
		};

	public RestClient(String location) {
		checkArgument(!isNullOrEmpty(location));
		base = URI.create(location);
		httpClient = HttpClients.createDefault();
	}

	@Override
	public void close() throws IOException {
		httpClient.close();
	}

	@Override
	public Account createAccount() {
		URI uri;

		try {
			uri = new URIBuilder(base)
				.setPath("/accounts/create")
				.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}

		try (CloseableHttpResponse response = httpClient.execute(new HttpPost(uri))) {
			int code = response.getStatusLine().getStatusCode();
			checkState(code == SC_CREATED, "Unexpected status code %d", code);
			Result<Account> r = mapper.readValue(response.getEntity().getContent(), ACCOUNT_RESULT);
			return r.result;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean removeAccount(int id) {
		URI uri;

		try {
			uri = new URIBuilder(base)
				.setPath("/accounts/" + id)
				.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}

		try (CloseableHttpResponse response = httpClient.execute(new HttpDelete(uri))) {
			int statusCode = response.getStatusLine().getStatusCode();
			switch (statusCode) {
				case SC_NOT_FOUND:
					return false;
				case SC_OK:
					return true;
				default:
					throw new IllegalStateException("Unexpected status code " + statusCode);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Account getAccount(int id) {
		URI uri;

		try {
			uri = new URIBuilder(base)
				.setPath("/accounts/" + id)
				.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}

		try (CloseableHttpResponse response = httpClient.execute(new HttpGet(uri))) {
			int statusCode = response.getStatusLine().getStatusCode();
			switch (statusCode) {
				case SC_NOT_FOUND:
					return null;
				case SC_OK:
					Result<Account> r = mapper.readValue(response.getEntity().getContent(), ACCOUNT_RESULT);
					return r.result;
				default:
					throw new IllegalStateException("Unexpected status code " + statusCode);
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean increaseAccountBalance(int id, long amount) {
		URI uri;

		try {
			uri = new URIBuilder(base)
				.setPath("/accounts/" + id + "/increase")
				.setParameter("amount", Long.toString(amount))
				.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}

		try (CloseableHttpResponse response = httpClient.execute(new HttpPost(uri))) {
			int statusCode = response.getStatusLine().getStatusCode();
			switch (statusCode) {
				case SC_NOT_FOUND:
					return false;
				case SC_OK:
					return true;
				default:
					Result<String> r = mapper.readValue(response.getEntity().getContent(), TEXT_RESULT);
					throw new IllegalStateException("Unexpected status code " + statusCode + ": " + r.message);
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public TransferResult execute(Transfer transfer) {
		URI uri;
		try {
			uri = new URIBuilder(base)
				.setPath("/transfer")
				.setParameter("src", Integer.toString(transfer.getSrcId()))
				.setParameter("dst", Integer.toString(transfer.getDstId()))
				.setParameter("amount", Long.toString(transfer.getAmount()))
				.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}

		try (CloseableHttpResponse response = httpClient.execute(new HttpPost(uri))) {
			int statusCode = response.getStatusLine().getStatusCode();
			Result<String> r = mapper.readValue(response.getEntity().getContent(), TEXT_RESULT);
			switch (statusCode) {
				case SC_OK:
				case SC_NOT_ACCEPTABLE:
					return TransferResult.valueOf(r.message);
				default:
					throw new IllegalStateException("Unexpected status code " + statusCode + ": " + r.message);
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public List<Transfer> getAccountLog(int id) {
		URI uri;

		try {
			uri = new URIBuilder(base)
				.setPath("/accounts/" + id + "/log")
				.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}

		try (CloseableHttpResponse response = httpClient.execute(new HttpGet(uri))) {
			int statusCode = response.getStatusLine().getStatusCode();
			switch (statusCode) {
				case SC_NOT_FOUND:
					return emptyList();
				case SC_OK:
					Result<List<Transfer>> r = mapper.readValue(response.getEntity().getContent(), ACCOUNT_LOG_RESULT);
					return r.result;
				default:
					throw new IllegalStateException("Unexpected status code " + statusCode);
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}

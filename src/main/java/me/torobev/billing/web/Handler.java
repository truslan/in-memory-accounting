package me.torobev.billing.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.torobev.billing.Account;
import me.torobev.billing.Transfer;
import me.torobev.billing.accounting.Accounting;
import me.torobev.billing.accounting.Accounting.TransferResult;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.util.regex.Pattern.compile;
import static javax.servlet.http.HttpServletResponse.*;
import static me.torobev.billing.accounting.Accounting.TransferResult.OK;
import static org.slf4j.LoggerFactory.getLogger;

public class Handler extends AbstractHandler {

	private final ObjectMapper mapper;
	private final Accounting accounting;

	private static final Logger LOGGER = getLogger(Handler.class);

	private static final String CONTENT_TYPE = "application/json; charset=utf-8";

	private static final Pattern ACCOUNT = compile("/accounts/(?<id>[\\d]+)/?$");
	private static final Pattern ACCOUNT_INCREASE = compile("/accounts/(?<id>[\\d]+)/increase/?$");
	private static final Pattern ACCOUNT_LOG = compile("/accounts/(?<id>[\\d]+)/log/?$");

	private static final Result<?> NOT_FOUND = new Result<>(SC_NOT_FOUND, "NOT_FOUND", null);
	private static final Result<?> NOT_ALLOWED = new Result<>(SC_METHOD_NOT_ALLOWED, "NOT_ALLOWED", null);


	Handler(ObjectMapper mapper, Accounting accounting) {
		this.mapper = mapper;
		this.accounting = accounting;
	}

	public static class Result<T> {

		@JsonProperty(value = "statusCode")
		public int statusCode;
		@JsonProperty(value = "message")
		public String message;
		@JsonProperty(value = "result")
		public T result;

		Result(@JsonProperty(value = "statusCode") int statusCode,
					 @JsonProperty(value = "message") String message,
					 @JsonProperty(value = "result") T result) {
			this.statusCode = statusCode;
			this.message = message;
			this.result = result;
		}
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
		try {
			response.setContentType(CONTENT_TYPE);

			Result<?> result = NOT_FOUND;
			Matcher matcher;

			// disable client caching
			response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
			response.addHeader("Pragma", "no-cache");
			response.addHeader("Expires", "0");

			if ("/transfer".equals(target) || "/transfer/".equals(target)) {
				result = transfer(request);
			} else if ("/accounts/create".equals(target) || "/accounts/create/".equals(target)) {
				result = createAccount(baseRequest, response);
			} else if ((matcher = ACCOUNT.matcher(target)).find()) {
				if ("DELETE".equals(request.getMethod())) {
					result = removeAccount(parseInt(matcher.group("id")));
				} else {
					result = showAccount(parseInt(matcher.group("id")));
				}
			} else if ((matcher = ACCOUNT_LOG.matcher(target)).find()) {
				result = showAccountLog(parseInt(matcher.group("id")));
			} else if ((matcher = ACCOUNT_INCREASE.matcher(target)).find()) {
				result = accountIncrease(parseInt(matcher.group("id")), request);
			}
			response.setStatus(result.statusCode);
			mapper.writeValue(response.getOutputStream(), result);
		} catch (IllegalArgumentException e) {
			LOGGER.error("Failed to handle request {}", target, e);
			response.setStatus(SC_NOT_ACCEPTABLE);
		} catch (RuntimeException | IOException e) {
			LOGGER.error("Failed to handle request {}", target, e);
			response.setStatus(SC_INTERNAL_SERVER_ERROR);
		} finally {
			baseRequest.setHandled(true);
		}
	}

	private Result<?> transfer(HttpServletRequest request) {
		if (!request.getMethod().equals("POST")) {
			return NOT_ALLOWED;
		}
		String amountStr = request.getParameter("amount");
		if (amountStr == null || amountStr.isEmpty()) {
			return new Result<>(SC_NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Amount required");
		}

		String fromStr = request.getParameter("src");
		if (fromStr == null || fromStr.isEmpty()) {
			return new Result<>(SC_NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Src account required");
		}

		String toStr = request.getParameter("dst");
		if (toStr == null || toStr.isEmpty()) {
			return new Result<>(SC_NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Dst account required");
		}

		TransferResult result = accounting.execute(new Transfer(parseInt(fromStr), parseInt(toStr), parseLong(amountStr)));
		return new Result<>(result == OK ? SC_OK : SC_NOT_ACCEPTABLE, result.toString(), null);
	}

	private Result<?> accountIncrease(int id, HttpServletRequest request) {
		if (!request.getMethod().equals("POST")) {
			return NOT_ALLOWED;
		}
		String amountStr = request.getParameter("amount");
		if (amountStr == null || amountStr.isEmpty()) {
			return new Result<>(SC_NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Amount required");
		}
		long amount = parseLong(amountStr);
		if (accounting.increaseAccountBalance(id, amount)) {
			return new Result<>(SC_OK, "OK", null);
		} else {
			return NOT_FOUND;
		}
	}

	private Result<?> createAccount(Request baseRequest, HttpServletResponse response) {
		if (baseRequest.getMethod().equals("POST")) {
			Account account = accounting.createAccount();
			response.addHeader("Location", "/accounts/" + account.getId());
			return new Result<>(SC_CREATED, "CREATED", account);
		} else {
			return NOT_ALLOWED;
		}
	}

	private Result<?> showAccount(int id) {
		Account account = accounting.getAccount(id);
		if (account == null) {
			return NOT_FOUND;
		} else {
			return new Result<>(SC_OK, "OK", account);
		}
	}

	private Result<?> removeAccount(int id) {
		if (accounting.removeAccount(id)) {
			return new Result<>(SC_OK, "OK", null);
		} else {
			return NOT_FOUND;
		}
	}


	private Result<?> showAccountLog(int id) {
		Account account = accounting.getAccount(id);
		if (account == null) {
			return NOT_FOUND;
		} else {
			List<Transfer> accountLog = accounting.getAccountLog(id);
			return new Result<>(SC_OK, "OK", accountLog);
		}
	}
}

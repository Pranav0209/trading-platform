package com.zkuba.trading.dto.response;

import java.util.List;

public record WatchlistResponse(Long id, String name, List<String> symbols) {}

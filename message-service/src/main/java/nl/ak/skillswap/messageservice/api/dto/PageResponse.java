package nl.ak.skillswap.messageservice.api.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        boolean hasMore
) {}

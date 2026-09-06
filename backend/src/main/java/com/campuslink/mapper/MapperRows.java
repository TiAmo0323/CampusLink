package com.campuslink.mapper;

import lombok.Data;

public final class MapperRows {
    private MapperRows() {}

    @Data
    public static class AggregateValue {
        private Long id;
        private Double metricValue;
    }

    @Data
    public static class CategoryCount {
        private String name;
        private Long countValue;
    }
}

package com.example.atelier.domain.dimension;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeValueGenerateResult {

    private int generated;

    private int skipped;

    private List<DimensionValue> values;
}

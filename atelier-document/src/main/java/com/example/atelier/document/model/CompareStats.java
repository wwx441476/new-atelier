package com.example.atelier.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareStats {
    private int added;
    private int removed;
    private int modified;
    private int moved;
}

package com.terraboxstudios.nanopay.death;

import java.util.List;

public interface RangeSearchable<T, R> {

    List<T> findByRange(R rangeLower, R rangeHigher);

}

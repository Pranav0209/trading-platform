package com.zkuba.trading.repository;

import com.zkuba.trading.domain.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {}

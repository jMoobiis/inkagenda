package br.com.inkagenda.domain.repository;

import br.com.inkagenda.domain.model.ItemPortfolio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPortfolioRepository extends JpaRepository<ItemPortfolio, Long> {

    List<ItemPortfolio> findAllByOrderByDisplayOrderAsc();

    List<ItemPortfolio> findByAtivoTrueOrderByDisplayOrderAsc();
}

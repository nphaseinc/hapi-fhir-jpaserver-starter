package ca.uhn.fhir.jpa.starter.custom.aggregation.dao;

import ca.uhn.fhir.jpa.entity.ResourceSearchView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface IResourceSearchViewExtDao extends JpaRepository<ResourceSearchView, Long> {

	@Query("SELECT v FROM ResourceSearchView v WHERE v.myResourceType = :resourceType AND v.myResourceId > :pid")
	List<ResourceSearchView> findByResourceId(@Param("pid") Long pid, @Param("resourceType") String resourceType, Pageable pageable);
}

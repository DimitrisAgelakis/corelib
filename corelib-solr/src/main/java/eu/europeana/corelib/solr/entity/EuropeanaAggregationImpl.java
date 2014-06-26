package eu.europeana.corelib.solr.entity;

import java.util.List;
import java.util.Map;

import javax.persistence.Transient;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.springframework.data.neo4j.annotation.GraphProperty;
import org.springframework.data.neo4j.annotation.NodeEntity;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Reference;

import eu.europeana.corelib.definitions.solr.entity.EuropeanaAggregation;
import eu.europeana.corelib.definitions.solr.entity.WebResource;
import eu.europeana.corelib.utils.StringArrayUtils;

@JsonSerialize(include = Inclusion.NON_EMPTY)
@NodeEntity(partial = true)
@Entity("EuropeanaAggregation")
public class EuropeanaAggregationImpl extends AbstractEdmEntityImpl implements EuropeanaAggregation {
	@Transient
	@Reference
	private List<WebResource> webResources;
	@GraphProperty
	private String aggregatedCHO;
	@GraphProperty
	private String[] aggregates;
	@GraphProperty(propertyType = String.class)
	private Map<String,List<String>> dcCreator;
	@GraphProperty
	private String edmLandingPage;
	@GraphProperty
	private String edmIsShownBy;
	@GraphProperty
	private String[] edmHasView;
	@GraphProperty(propertyType = String.class)
	private Map<String,List<String>> edmCountry;
	@GraphProperty(propertyType = String.class)
	private Map<String,List<String>> edmLanguage;
	@GraphProperty(propertyType = String.class)
	private Map<String,List<String>> edmRights;
	private String edmPreview ="";

	@Override
	public String getAggregatedCHO() {
		return this.aggregatedCHO;
	}

	@Override
	public void setAggregatedCHO(String aggregatedCHO) {
		this.aggregatedCHO = aggregatedCHO;
	}

	@Override
	public String[] getAggregates() {
		return (StringArrayUtils.isNotBlank(this.aggregates) ? this.aggregates.clone() : null);
	}

	@Override
	public void setAggregates(String[] aggregates) {
		this.aggregates = aggregates!=null?aggregates.clone():null;
	}

	@Override
	public Map<String,List<String>> getDcCreator() {
		return this.dcCreator;
	}

	@Override
	public void setDcCreator(Map<String,List<String>> dcCreator) {
		this.dcCreator = dcCreator;
	}

	@Override
	public String getEdmLandingPage() {
		return this.edmLandingPage;
	}

	@Override
	public void setEdmLandingPage(String edmLandingPage) {
		this.edmLandingPage = edmLandingPage;
	}

	@Override
	public String getEdmIsShownBy() {
		return this.edmIsShownBy;
	}

	@Override
	public void setEdmIsShownBy(String edmIsShownBy) {
		this.edmIsShownBy = edmIsShownBy;
	}

	@Override
	public String[] getEdmHasView() {
		return this.edmHasView;
	}

	@Override
	public void setEdmHasView(String[] edmHasView) {
		this.edmHasView = edmHasView!=null?edmHasView.clone():null;
	}

	@Override
	public Map<String,List<String>> getEdmCountry() {
		return this.edmCountry;
	}

	@Override
	public void setEdmCountry(Map<String,List<String>> edmCountry) {
		this.edmCountry = edmCountry;
	}

	@Override
	public Map<String,List<String>> getEdmLanguage() {
		return this.edmLanguage;
	}

	@Override
	public void setEdmLanguage(Map<String,List<String>> edmLanguage) {
		this.edmLanguage = edmLanguage;
	}

	@Override
	public Map<String,List<String>> getEdmRights() {
		return this.edmRights;
	}

	@Override
	public void setEdmRights(Map<String,List<String>> edmRights) {
		this.edmRights = edmRights;
	}

	@Override
	public List<? extends WebResource> getWebResources() {
		return webResources;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setWebResources(List<? extends WebResource> webResources) {
		this.webResources = (List<WebResource>) webResources;
	}
	
	@Override
	public String getEdmPreview(){
		
		return this.edmPreview;
	}
	
	@Override
	public void setEdmPreview(String edmPreview){
		this.edmPreview = edmPreview;
	}
}

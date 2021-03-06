/*
 * Copyright 2007-2012 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved 
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *  
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under 
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of 
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under 
 *  the Licence.
 */

package eu.europeana.corelib.db.entity.relational.abstracts;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.commons.lang.StringUtils;

import eu.europeana.corelib.definitions.db.entity.RelationalDatabase;
import eu.europeana.corelib.definitions.db.entity.relational.abstracts.EuropeanaUserObject;
import eu.europeana.corelib.definitions.solr.DocType;

/**
 * Super class Saved europeana objects in my Europeana
 * 
 * @author Willem-Jan Boogerd <www.eledge.net/contact>
 */
@MappedSuperclass
public abstract class EuropeanaUserObjectImpl extends UserConnectedImpl<Long> implements RelationalDatabase, EuropeanaUserObject {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;

	@Column(length = FIELDSIZE_EUROPEANA_URI)
	private String europeanaUri;

	@Column(length = FIELDSIZE_TITLE)
	private String title;

	@Column(length = FIELDSIZE_EUROPEANA_OBJECT)
	private String europeanaObject;

	@Column(length = FIELDSIZE_DOCTYPE)
	@Enumerated(EnumType.STRING)
	private DocType docType = DocType.IMAGE;

	@Column
	@Temporal(TemporalType.TIMESTAMP)
	private Date dateSaved;

	/**
	 * GETTERS & SETTTERS
	 */

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public Date getDateSaved() {
		return new Date(dateSaved.getTime());
	}

	@Override
	public void setDateSaved(Date dateSaved) {
		this.dateSaved = new Date(dateSaved.getTime());
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public void setTitle(String title) {
		this.title = title;
	}

	@Override
	public String getEuropeanaObject() {
		if (StringUtils.isNotBlank(europeanaObject)) {
			try {
				return URLEncoder.encode(europeanaObject, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				return europeanaObject;
			}
		}
		return null;
	}

	@Override
	public void setEuropeanaObject(String europeanaObject) {
		this.europeanaObject = europeanaObject;
	}

	@Override
	public String getEuropeanaUri() {
		return europeanaUri;
	}

	@Override
	public void setEuropeanaUri(String europeanaUri) {
		this.europeanaUri = europeanaUri;
	}

	@Override
	public DocType getDocType() {
		return docType;
	}

	@Override
	public void setDocType(DocType docType) {
		this.docType = docType;
	}
}

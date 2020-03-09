package org.wirabumi.gen.oez.ad_process;

import java.util.Date;

public class DraftWorkEffortBean {
	
	private String m_production_id, documentno, m_productionplan_id, ma_wrphase_id,
	ma_workrequirement_id, wr_number, m_productionline_id,
	m_product_id, product_name, product_code;
	private Date starttime, endtime;
	private boolean processed;
	private double neededquantity, rejectedquantity;
	private long wrphase_seqno;
	public String getM_production_id() {
		return m_production_id;
	}
	public void setM_production_id(String m_production_id) {
		this.m_production_id = m_production_id;
	}
	public String getDocumentno() {
		return documentno;
	}
	public void setDocumentno(String documentno) {
		this.documentno = documentno;
	}
	public String getM_productionplan_id() {
		return m_productionplan_id;
	}
	public void setM_productionplan_id(String m_productionplan_id) {
		this.m_productionplan_id = m_productionplan_id;
	}
	public String getMa_wrphase_id() {
		return ma_wrphase_id;
	}
	public void setMa_wrphase_id(String ma_wrphase_id) {
		this.ma_wrphase_id = ma_wrphase_id;
	}
	public String getMa_workrequirement_id() {
		return ma_workrequirement_id;
	}
	public void setMa_workrequirement_id(String ma_workrequirement_id) {
		this.ma_workrequirement_id = ma_workrequirement_id;
	}
	public String getWr_number() {
		return wr_number;
	}
	public void setWr_number(String wr_number) {
		this.wr_number = wr_number;
	}
	public String getM_productionline_id() {
		return m_productionline_id;
	}
	public void setM_productionline_id(String m_productionline_id) {
		this.m_productionline_id = m_productionline_id;
	}
	public String getM_product_id() {
		return m_product_id;
	}
	public void setM_product_id(String m_product_id) {
		this.m_product_id = m_product_id;
	}
	public String getProduct_name() {
		return product_name;
	}
	public void setProduct_name(String product_name) {
		this.product_name = product_name;
	}
	public String getProduct_code() {
		return product_code;
	}
	public void setProduct_code(String product_code) {
		this.product_code = product_code;
	}
	public Date getStarttime() {
		return starttime;
	}
	public void setStarttime(Date starttime) {
		this.starttime = starttime;
	}
	public Date getEndtime() {
		return endtime;
	}
	public void setEndtime(Date endtime) {
		this.endtime = endtime;
	}
	public boolean isProcessed() {
		return processed;
	}
	public void setProcessed(boolean processed) {
		this.processed = processed;
	}
	public double getNeededquantity() {
		return neededquantity;
	}
	public void setNeededquantity(double neededquantity) {
		this.neededquantity = neededquantity;
	}
	public double getRejectedquantity() {
		return rejectedquantity;
	}
	public void setRejectedquantity(double rejectedquantity) {
		this.rejectedquantity = rejectedquantity;
	}
	public long getWrphase_seqno() {
		return wrphase_seqno;
	}
	public void setWrphase_seqno(long wrphase_seqno) {
		this.wrphase_seqno = wrphase_seqno;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((documentno == null) ? 0 : documentno.hashCode());
		result = prime * result + ((endtime == null) ? 0 : endtime.hashCode());
		result = prime * result + ((m_product_id == null) ? 0 : m_product_id.hashCode());
		result = prime * result + ((m_production_id == null) ? 0 : m_production_id.hashCode());
		result = prime * result + ((m_productionline_id == null) ? 0 : m_productionline_id.hashCode());
		result = prime * result + ((m_productionplan_id == null) ? 0 : m_productionplan_id.hashCode());
		result = prime * result + ((ma_workrequirement_id == null) ? 0 : ma_workrequirement_id.hashCode());
		result = prime * result + ((ma_wrphase_id == null) ? 0 : ma_wrphase_id.hashCode());
		long temp;
		temp = Double.doubleToLongBits(neededquantity);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + (processed ? 1231 : 1237);
		result = prime * result + ((product_code == null) ? 0 : product_code.hashCode());
		result = prime * result + ((product_name == null) ? 0 : product_name.hashCode());
		temp = Double.doubleToLongBits(rejectedquantity);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((starttime == null) ? 0 : starttime.hashCode());
		result = prime * result + ((wr_number == null) ? 0 : wr_number.hashCode());
		result = prime * result + (int) (wrphase_seqno ^ (wrphase_seqno >>> 32));
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DraftWorkEffortBean other = (DraftWorkEffortBean) obj;
		if (documentno == null) {
			if (other.documentno != null)
				return false;
		} else if (!documentno.equals(other.documentno))
			return false;
		if (endtime == null) {
			if (other.endtime != null)
				return false;
		} else if (!endtime.equals(other.endtime))
			return false;
		if (m_product_id == null) {
			if (other.m_product_id != null)
				return false;
		} else if (!m_product_id.equals(other.m_product_id))
			return false;
		if (m_production_id == null) {
			if (other.m_production_id != null)
				return false;
		} else if (!m_production_id.equals(other.m_production_id))
			return false;
		if (m_productionline_id == null) {
			if (other.m_productionline_id != null)
				return false;
		} else if (!m_productionline_id.equals(other.m_productionline_id))
			return false;
		if (m_productionplan_id == null) {
			if (other.m_productionplan_id != null)
				return false;
		} else if (!m_productionplan_id.equals(other.m_productionplan_id))
			return false;
		if (ma_workrequirement_id == null) {
			if (other.ma_workrequirement_id != null)
				return false;
		} else if (!ma_workrequirement_id.equals(other.ma_workrequirement_id))
			return false;
		if (ma_wrphase_id == null) {
			if (other.ma_wrphase_id != null)
				return false;
		} else if (!ma_wrphase_id.equals(other.ma_wrphase_id))
			return false;
		if (Double.doubleToLongBits(neededquantity) != Double.doubleToLongBits(other.neededquantity))
			return false;
		if (processed != other.processed)
			return false;
		if (product_code == null) {
			if (other.product_code != null)
				return false;
		} else if (!product_code.equals(other.product_code))
			return false;
		if (product_name == null) {
			if (other.product_name != null)
				return false;
		} else if (!product_name.equals(other.product_name))
			return false;
		if (Double.doubleToLongBits(rejectedquantity) != Double.doubleToLongBits(other.rejectedquantity))
			return false;
		if (starttime == null) {
			if (other.starttime != null)
				return false;
		} else if (!starttime.equals(other.starttime))
			return false;
		if (wr_number == null) {
			if (other.wr_number != null)
				return false;
		} else if (!wr_number.equals(other.wr_number))
			return false;
		if (wrphase_seqno != other.wrphase_seqno)
			return false;
		return true;
	}
	
	

}

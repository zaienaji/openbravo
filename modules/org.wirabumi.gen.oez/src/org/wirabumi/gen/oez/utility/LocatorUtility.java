package org.wirabumi.gen.oez.utility;

import java.util.ArrayList;
import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.geography.Country;
import org.openbravo.model.common.geography.Location;
import org.openbravo.model.common.geography.Region;

public class LocatorUtility {
  /**
   * 
   * @param partner
   * @param phone
   * @param addres1
   * @param addres2
   * @param cityName
   * @param postalCode
   * @param region
   * @param country
   * @return
   */
  public static org.openbravo.model.common.businesspartner.Location searchBPartnerLocation(
      BusinessPartner partner,Location pLocaltion, String phone, String addres1, String addres2, String cityName,
      String postalCode, Region region, Country country) {
    org.openbravo.model.common.businesspartner.Location BPLocation = null;
    try {
      Location location =pLocaltion==null? searchLocation(addres1, addres2, cityName, postalCode, region, country):searchLocation(pLocaltion.getAddressLine1(), pLocaltion.getAddressLine2(), pLocaltion.getCityName(),pLocaltion.getPostalCode(), pLocaltion.getRegion(), pLocaltion.getCountry());
      String whereClause = "as bloc where bloc.businessPartner=? and bloc.locationAddress=?";
      List<Object> paramList = new ArrayList<Object>();
      paramList.add(partner);
      paramList.add(location);
      if(phone!=null){
    	  whereClause=whereClause.concat(" and bloc.phone=?");
      paramList.add(phone);
      }
      OBQuery<org.openbravo.model.common.businesspartner.Location> busPartnerLocation = OBDal
          .getInstance().createQuery(org.openbravo.model.common.businesspartner.Location.class,
              whereClause, paramList);
      List<org.openbravo.model.common.businesspartner.Location> busLocation = busPartnerLocation
          .list();
      if (busLocation.size() > 0) {
        BPLocation = busLocation.get(0);
      } else {
        BPLocation = OBProvider.getInstance().get(
            org.openbravo.model.common.businesspartner.Location.class);
        BPLocation.setBusinessPartner(partner);
        BPLocation.setLocationAddress(location);
        BPLocation.setName(location.getIdentifier());
        BPLocation.setPhone(phone);
        OBDal.getInstance().save(BPLocation);
      }

    } catch (Exception e) {
      throw new OBException(e.getLocalizedMessage());
    }
    return BPLocation;
  }

  /**
   * 
   * @param addres1
   * @param addres2
   * @param cityName
   * @param postalCode
   * @param region
   * @param country
   * @return
   */
  public static Location searchLocation(String addres1, String addres2, String cityName,
      String postalCode, Region region, Country country) {
    Location locator = null;
    try {
      StringBuilder whereClause = new StringBuilder();
      whereClause.append(" as loc where loc.country.id=? ");
      List<Object> paramList = new ArrayList<Object>();
      paramList.add(country == null ? null : country.getId());
      if(addres1!=null){
    	  whereClause.append("and loc.addressLine1=? ");
          paramList.add(addres1);

      }
      if(addres2!=null){
    	  whereClause.append("and loc.addressLine2 = ? ");
    	  paramList.add(addres2);
      }
      if(postalCode!=null){
    	  whereClause.append("and loc.postalCode=? ");
    	  paramList.add(postalCode);
      }
      if(cityName!=null){
    	  whereClause.append("and loc.cityName=? ");
      paramList.add(cityName);
      }
      if(region!=null){
    	  whereClause.append("and loc.region.id=? ");
    	  paramList.add(region == null ? null : region.getId());
      }
      OBQuery<Location> locationList = OBDal.getInstance().createQuery(Location.class, whereClause.toString(),
          paramList);
      List<Location> locList = locationList.list();
      if (locList.size() > 0) {
        locator = locList.get(0);
      } else {
        locator = createLocator(addres1, addres2, cityName, postalCode, region, country);
      }
    } catch (Exception e) {
      throw new OBException(e.getLocalizedMessage());
    }
    return locator;
  }

  /**
   * 
   * @param addres1
   * @param addres2
   * @param cityName
   * @param postalCode
   * @param region
   * @param country
   * @return
   */
  public static Location createLocator(String addres1, String addres2, String cityName,
      String postalCode, Region region, Country country) {
    Location locator = null;
    try {
      locator = OBProvider.getInstance().get(Location.class);
      locator.setAddressLine1(addres1);
      locator.setAddressLine2(addres2);
      locator.setCityName(cityName);
      locator.setPostalCode(postalCode);
      locator.setRegion(region);
      locator.setCountry(country);
      OBDal.getInstance().save(locator);
    } catch (Exception e) {
      throw new OBException(e.getLocalizedMessage());
    }
    return locator;
  }

}

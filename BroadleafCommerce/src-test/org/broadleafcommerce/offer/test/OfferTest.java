/*
 * Copyright 2008-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.broadleafcommerce.offer.test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.broadleafcommerce.catalog.dao.ProductDao;
import org.broadleafcommerce.catalog.dao.SkuDao;
import org.broadleafcommerce.catalog.domain.Product;
import org.broadleafcommerce.catalog.domain.ProductImpl;
import org.broadleafcommerce.catalog.domain.Sku;
import org.broadleafcommerce.offer.dao.CustomerOfferDao;
import org.broadleafcommerce.offer.dao.OfferCodeDao;
import org.broadleafcommerce.offer.dao.OfferDao;
import org.broadleafcommerce.offer.domain.CustomerOffer;
import org.broadleafcommerce.offer.domain.CustomerOfferImpl;
import org.broadleafcommerce.offer.domain.Offer;
import org.broadleafcommerce.offer.domain.OfferCode;
import org.broadleafcommerce.offer.domain.OfferInfo;
import org.broadleafcommerce.offer.service.OfferService;
import org.broadleafcommerce.offer.service.type.OfferDeliveryType;
import org.broadleafcommerce.offer.service.type.OfferDiscountType;
import org.broadleafcommerce.offer.service.type.OfferType;
import org.broadleafcommerce.order.domain.DiscreteOrderItem;
import org.broadleafcommerce.order.domain.DiscreteOrderItemImpl;
import org.broadleafcommerce.order.domain.FulfillmentGroup;
import org.broadleafcommerce.order.domain.FulfillmentGroupImpl;
import org.broadleafcommerce.order.domain.Order;
import org.broadleafcommerce.order.service.CartService;
import org.broadleafcommerce.profile.domain.Customer;
import org.broadleafcommerce.profile.service.CustomerService;
import org.broadleafcommerce.test.dataprovider.SkuDaoDataProvider;
import org.broadleafcommerce.test.integration.BaseTest;
import org.broadleafcommerce.util.money.Money;
import org.springframework.test.annotation.Rollback;
import org.testng.annotations.Test;

public class OfferTest extends BaseTest {

    @Resource
    private OfferService offerService;

    @Resource
    private CustomerService customerService;

    @Resource
    private CartService cartService;

    @Resource
    private OfferDao offerDao;

    @Resource
    private CustomerOfferDao customerOfferDao;

    @Resource
    private SkuDao skuDao;

    @Resource
    private ProductDao productDao;

    @Resource
    private OfferCodeDao offerCodeDao;

    private long sku1;
    private long sku2;

    @Test(groups = { "offerCreateSku1" }, dataProvider = "basicSku", dataProviderClass = SkuDaoDataProvider.class)
    @Rollback(false)
    public void createSku1(Sku sku) {
        sku.setSalePrice(new Money(BigDecimal.valueOf(10.0)));
        sku.setRetailPrice(new Money(BigDecimal.valueOf(15.0)));
        sku.setName("test1");
        assert sku.getId() == null;
        sku = skuDao.save(sku);
        assert sku.getId() != null;
        sku1 = sku.getId();
    }

    @Test(groups = { "offerCreateSku2" }, dataProvider = "basicSku", dataProviderClass = SkuDaoDataProvider.class)
    @Rollback(false)
    public void createSku2(Sku sku) {
        sku.setSalePrice(new Money(BigDecimal.valueOf(10.0)));
        sku.setRetailPrice(new Money(BigDecimal.valueOf(15.0)));
        sku.setName("test2");
        assert sku.getId() == null;
        sku = skuDao.save(sku);
        assert sku.getId() != null;
        sku2 = sku.getId();
    }

    @Test(groups =  {"offerUsedForPricing"}, dependsOnGroups = { "offerCreateSku1", "offerCreateSku2" })
    public void testOfferUsedForPricing() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 10D, null, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 20D, null, true, 1));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, "discreteOrderItem.sku.id == "+sku1, true, true, 10));
        order.addAddedOfferCode(createOfferCode("3 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 3, null, "discreteOrderItem.sku.id != "+sku1, true, true, 10));
        order.addAddedOfferCode(createOfferCode("1.20 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 1.20, null, null, true, true, 10));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getAdjustmentPrice().equals(new Money(31.80D)));
    }

    @Test(groups =  {"testOfferNotStackableItemOffers"}, dependsOnGroups = { "offerUsedForPricing"})
    public void testOfferNotStackableItemOffers() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 100D, null, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 100D, null, true, 2));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, "discreteOrderItem.sku.id == "+sku1, false, true, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 30, null, "discreteOrderItem.sku.id == "+sku1, true, true, 1));
        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, "discreteOrderItem.sku.id != "+sku1, true, true, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 30, null, "discreteOrderItem.sku.id != "+sku1, false, true, 1));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getSubTotal().equals(new Money(252D)));
    }

    @Test(groups =  {"testOfferNotCombinableItemOffers"}, dependsOnGroups = { "testOfferNotStackableItemOffers"})
    public void testOfferNotCombinableItemOffers() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 100D, null, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 100D, null, true, 2));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, "discreteOrderItem.sku.id == "+sku1, true, true, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 30, null, "discreteOrderItem.sku.id == "+sku1, true, false, 1));
        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, "discreteOrderItem.sku.id != "+sku1, true, false, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 30, null, "discreteOrderItem.sku.id != "+sku1, true, true, 1));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getSubTotal().equals(new Money(340D)));
    }

    @Test(groups =  {"testOfferLowerSalePrice"}, dependsOnGroups = { "testOfferNotCombinableItemOffers"})
    public void testOfferLowerSalePrice() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 100D, 50D, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 100D, null, true, 2));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, "discreteOrderItem.sku.id == "+sku1, true, true, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 30, null, "discreteOrderItem.sku.id == "+sku1, true, true, 1));
        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, "discreteOrderItem.sku.id != "+sku1, true, true, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 30, null, "discreteOrderItem.sku.id != "+sku1, true, true, 1));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getSubTotal().equals(new Money(212D)));
    }

    @Test(groups =  {"testOfferLowerSalePriceWithNotCombinableOffer"}, dependsOnGroups = { "testOfferLowerSalePrice"})
    public void testOfferLowerSalePriceWithNotCombinableOffer() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 100D, 50D, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 100D, null, true, 2));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, null, true, true, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 30, null, null, true, false, 1));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getSubTotal().equals(new Money(240D)));
    }

    @Test(groups =  {"testOfferLowerSalePriceWithNotCombinableOfferAndInformation"}, dependsOnGroups = { "testOfferLowerSalePriceWithNotCombinableOffer"})
    public void testOfferLowerSalePriceWithNotCombinableOfferAndInformation() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 100D, 50D, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 100D, null, true, 2));

        OfferCode offerCode1 = createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, null, true, true, 1);
        OfferCode offerCode2 = createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 30, null, null, true, false, 1);

        order.addAddedOfferCode(offerCode1);
        order.addAddedOfferCode(offerCode2);

        OfferInfo info1 = offerDao.createOfferInfo();
        info1.getFieldValues().put("key1", "value1");
        order.getAdditionalOfferInformation().put(offerCode1.getOffer(), info1);
        OfferInfo info2 = offerDao.createOfferInfo();
        info2.getFieldValues().put("key2", "value2");
        order.getAdditionalOfferInformation().put(offerCode2.getOffer(), info2);

        cartService.save(order, true);

        assert (order.getSubTotal().equals(new Money(240D)));

        order = cartService.findOrderById(order.getId());
        assert(order.getAdditionalOfferInformation().get(offerCode1.getOffer()).equals(info1));
    }

    @Test(groups =  {"testOfferLowerSalePriceWithNotCombinableOffer2"}, dependsOnGroups = { "testOfferLowerSalePriceWithNotCombinableOffer"})
    public void testOfferLowerSalePriceWithNotCombinableOffer2() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 100D, 50D, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 100D, 50D, true, 2));

        order.addAddedOfferCode(createOfferCode("25 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 25, null, null, true, true, 1));
        order.addAddedOfferCode(createOfferCode("35 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 35, null, null, true, true, 1));
        order.addAddedOfferCode(createOfferCode("45 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 45, null, null, true, false, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 30, null, null, true, true, 1));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getAdjustmentPrice().equals(new Money(130D)));
    }

    @Test(groups =  {"testOfferNotStackableOrderOffers"}, dependsOnGroups = { "testOfferLowerSalePriceWithNotCombinableOffer2"})
    public void testOfferNotStackableOrderOffers() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 100D, null, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 100D, null, true, 2));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Order Offer", OfferType.ORDER, OfferDiscountType.PERCENT_OFF, 20, null, "order.subTotal.getAmount() >= 400", true, true, 1));
        order.addAddedOfferCode(createOfferCode("50 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 50, null, "order.subTotal.getAmount() >= 400", false, true, 1));
        order.addAddedOfferCode(createOfferCode("100 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 100, null, "order.subTotal.getAmount() >= 400", false, true, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 30, null, "order.subTotal.getAmount() < 400", false, true, 1));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getAdjustmentPrice().equals(new Money(240D)));
    }

    @Test(groups =  {"testOfferNotCombinableOrderOffers"}, dependsOnGroups = { "testOfferNotStackableOrderOffers"})
    public void testOfferNotCombinableOrderOffers() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 100D, null, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 100D, null, true, 2));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Order Offer", OfferType.ORDER, OfferDiscountType.PERCENT_OFF, 20, null, null, true, true, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 30, null, null, true, true, 1));
        order.addAddedOfferCode(createOfferCode("50 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 50, null, null, true, false, 1));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getAdjustmentPrice().equals(new Money(290D)));
    }

    @Test(groups =  {"testOfferNotCombinableOrderOffersWithItemOffer"}, dependsOnGroups = { "testOfferNotCombinableOrderOffers"})
    public void testOfferNotCombinableOrderOffersWithItemOffer() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 100D, null, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 100D, null, true, 2));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, null, true, false, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 10, null, null, true, true, 1));
        order.addAddedOfferCode(createOfferCode("30 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 15, null, null, true, true, 1));
        order.addAddedOfferCode(createOfferCode("80 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 90, null, null, true, false, 1));
        order.addAddedOfferCode(createOfferCode("50 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 50, null, null, true, true, 1));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getAdjustmentPrice().equals(new Money(250D)));
    }

    @Test(groups =  {"testGlobalOffers"}, dependsOnGroups = { "testOfferNotCombinableOrderOffersWithItemOffer"})
    public void testGlobalOffers() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));


        order.addOrderItem(createDiscreteOrderItem(sku1, 10D, null, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 20D, null, true, 1));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, "discreteOrderItem.sku.id == "+sku1, true, true, 10));
        order.addAddedOfferCode(createOfferCode("3 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 3, null, "discreteOrderItem.sku.id != "+sku1, true, true, 10));

        Offer offer = createOffer("1.20 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 1.20, null, null, true, true, 10);
        offer.setDeliveryType(OfferDeliveryType.AUTOMATIC);
        offerDao.save(offer);

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getAdjustmentPrice().equals(new Money(31.80D)));
    }

    @Test(groups =  {"testCustomerAssociatedOffers"}, dependsOnGroups = { "testGlobalOffers"})
    public void testCustomerAssociatedOffers() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));


        order.addOrderItem(createDiscreteOrderItem(sku1, 10D, null, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 20D, null, true, 1));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.PERCENT_OFF, 20, null, "discreteOrderItem.sku.id == "+sku1, true, true, 10));
        order.addAddedOfferCode(createOfferCode("3 Dollars Off Item Offer", OfferType.ORDER_ITEM, OfferDiscountType.AMOUNT_OFF, 3, null, "discreteOrderItem.sku.id != "+sku1, true, true, 10));

        Offer offer = createOffer("1.20 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 1.20, null, null, true, true, 10);
        offer.setDeliveryType(OfferDeliveryType.MANUAL);
        offerDao.save(offer);
        CustomerOffer customerOffer = new CustomerOfferImpl();
        customerOffer.setCustomer(order.getCustomer());
        customerOffer.setOffer(offer);
        customerOfferDao.save(customerOffer);

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);

        assert (order.getAdjustmentPrice().equals(new Money(31.80D)));
    }

    @Test(groups =  {"testFulfillmentGroupOffers"}, dependsOnGroups = { "testCustomerAssociatedOffers"})
    public void testFulfillmentGroupOffers() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        order.setFulfillmentGroups(createFulfillmentGroups("standard", 5D, order));

        order.addOrderItem(createDiscreteOrderItem(sku1, 10D, null, true, 2));
        order.addOrderItem(createDiscreteOrderItem(sku2, 20D, null, true, 1));

        order.addAddedOfferCode(createOfferCode("20 Percent Off Item Offer", OfferType.FULFILLMENT_GROUP, OfferDiscountType.PERCENT_OFF, 20, null, null, true, true, 10));
        order.addAddedOfferCode(createOfferCode("3 Dollars Off Item Offer", OfferType.FULFILLMENT_GROUP, OfferDiscountType.AMOUNT_OFF, 3, null, null, true, true, 10));

        List<Offer> offers = offerService.buildOfferListForOrder(order);
        offerService.applyOffersToOrder(offers, order);
        offerService.applyFulfillmentGroupOffers(order.getFulfillmentGroups().get(0));

        assert (order.getFulfillmentGroups().get(0).getShippingPrice().equals(new Money(1.6D)));
    }

    @Test(groups =  {"testOfferDelete"}, dependsOnGroups = { "testFulfillmentGroupOffers"})
    public void testOfferDelete() throws Exception {
        CustomerOffer customerOffer = customerOfferDao.create();
        Customer customer = createCustomer();
        Long customerId = customer.getId();
        customerOffer.setCustomer(customerService.saveCustomer(customer));

        Offer offer = createOffer("1.20 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 1.20, null, null, true, true, 10);
        offer = offerService.save(offer);
        Long offerId = offer.getId();
        offerDao.delete(offer);
        Offer deletedOffer = offerDao.readOfferById(offerId);
        assert deletedOffer == null;

        offer = createOffer("1.20 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 1.20, null, null, true, true, 10);
        offer = offerService.save(offer);

        customerOffer.setOffer(offer);
        customerOffer = customerOfferDao.save(customerOffer);
        Long customerOfferId = customerOffer.getId();
        customerOffer = customerOfferDao.readCustomerOfferById(customerOfferId);
        assert(customerOffer != null);

        Customer customer2 = createCustomer();
        customerOffer.setCustomer(customerService.saveCustomer(customer2));
        customerOffer = customerOfferDao.save(customerOffer);

        assert !customerOffer.getCustomer().getId().equals(customerId);

        customerOfferDao.delete(customerOffer);
        customerOffer = customerOfferDao.readCustomerOfferById(customerOfferId);

        assert(customerOffer == null);
    }

    @Test(groups =  {"testReadAllOffers"}, dependsOnGroups = { "testOfferDelete"})
    public void testReadAllOffers() throws Exception {
        Offer offer = createOffer("1.20 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 1.20, null, null, true, true, 10);
        offer.setDeliveryType(OfferDeliveryType.MANUAL);
        offerService.save(offer);
        List<Offer> allOffers = offerService.findAllOffers();
        assert allOffers != null && allOffers.isEmpty() == false;
    }

    @Test(groups =  {"testOfferCodeDao"}, dependsOnGroups = { "testReadAllOffers"})
    public void testOfferCodeDao() throws Exception {
        String offerCodeString = "AJ's Code";
        OfferCode offerCode = createOfferCode("1.20 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 1.20, null, null, true, true, 10);
        offerCode.setOfferCode(offerCodeString);
        offerCode = offerCodeDao.save(offerCode);
        Long offerCodeId = offerCode.getId();
        assert offerCode.getOfferCode().equals(offerCodeString);

        Offer offer = offerCode.getOffer();
        Offer storedOffer = offerService.lookupOfferByCode(offerCodeString);
        assert offer.getId().equals(storedOffer.getId());

        OfferCode newOfferCode = offerCodeDao.readOfferCodeById(offerCodeId);
        assert newOfferCode.getOfferCode().equals(offerCode.getOfferCode());

        newOfferCode = offerCodeDao.readOfferCodeByCode(offerCodeString);
        assert newOfferCode.getOfferCode().equals(offerCode.getOfferCode());
        offerCodeId = newOfferCode.getId();
        offerCodeDao.delete(newOfferCode);

        OfferCode deletedOfferCode = offerCodeDao.readOfferCodeById(offerCodeId);
        assert deletedOfferCode == null;
    }

    @Test(groups =  {"testCustomerOffers"}, dependsOnGroups = { "testOfferCodeDao"})
    public void testCustomerOffers() throws Exception {
        Order order = cartService.createNewCartForCustomer(createCustomer());
        Offer offer = createOffer("1.20 Dollars Off Order Offer", OfferType.ORDER, OfferDiscountType.AMOUNT_OFF, 1.20, null, null, true, true, 10);
        CustomerOffer customerOffer = new CustomerOfferImpl();
        customerOffer.setCustomer(order.getCustomer());
        customerOffer.setOffer(offer);
        customerOffer = customerOfferDao.save(customerOffer);
        CustomerOffer customerOfferTest = customerOfferDao.readCustomerOfferById(customerOffer.getId());

        assert (customerOffer.getId().equals(customerOfferTest.getId()));
    }

    private Customer createCustomer() {
        Customer customer = customerService.createCustomerFromId(null);
        return customer;
    }

    private List<FulfillmentGroup> createFulfillmentGroups(String method, Double shippingPrice, Order order) {
        List<FulfillmentGroup> groups = new ArrayList<FulfillmentGroup>();
        FulfillmentGroup group = new FulfillmentGroupImpl();
        group.setMethod(method);
        groups.add(group);
        group.setRetailShippingPrice(new Money(shippingPrice));
        group.setOrder(order);

        return groups;
    }

    private DiscreteOrderItem createDiscreteOrderItem(Long skuId, Double retailPrice, Double salePrice, boolean isDiscountable, int quantity) {
        DiscreteOrderItemImpl item = new DiscreteOrderItemImpl();
        Sku sku = skuDao.readSkuById(skuId);
        sku.setRetailPrice(new Money(retailPrice));
        if (salePrice != null) {
            sku.setSalePrice(new Money(salePrice));
        } else {
            sku.setSalePrice(null);
        }
        sku.setDiscountable(isDiscountable);

        skuDao.save(sku);

        item.setSku(sku);
        item.setQuantity(quantity);
        Product product = new ProductImpl();
        product.setName("test");
        product.getSkus().add(sku);

        productDao.save(product);

        item.setProduct(product);

        return item;
    }

    private OfferCode createOfferCode(String offerName, OfferType offerType, OfferDiscountType discountType, double value, String customerRule, String orderRule, boolean stackable, boolean combinable, int priority) {
        OfferCode offerCode = offerCodeDao.create();
        Offer offer = createOffer(offerName, offerType, discountType, value, customerRule, orderRule, stackable, combinable, priority);
        offerCode.setOffer(offer);
        offerCode.setOfferCode("OPRAH");
        offerCodeDao.save(offerCode);
        return offerCode;
    }

    private Offer createOffer(String offerName, OfferType offerType, OfferDiscountType discountType, double value, String customerRule, String orderRule, boolean stackable, boolean combinable, int priority) {
        Offer offer = offerDao.create();
        offer.setName(offerName);
        offer.setStartDate(new Date());
        Calendar calendar = Calendar.getInstance();
        calendar.roll(Calendar.DATE, -1);
        offer.setStartDate(calendar.getTime());
        calendar.roll(Calendar.DATE, 2);
        offer.setEndDate(calendar.getTime());
        offer.setType(offerType);
        offer.setDiscountType(discountType);
        offer.setValue(new Money(value));
        offer.setDeliveryType(OfferDeliveryType.CODE);
        offer.setStackable(stackable);
        offer.setAppliesToOrderRules(orderRule);
        offer.setAppliesToCustomerRules(customerRule);
        offer.setCombinableWithOtherOffers(combinable);
        offer.setPriority(priority);
        offerDao.save(offer);
        return offer;
    }


}

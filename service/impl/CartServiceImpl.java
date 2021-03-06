package net.messi.early.service.impl;

import com.alibaba.fastjson.JSON;
import net.messi.early.VO.CartTotal;
import net.messi.early.VO.CouponVo;
import net.messi.early.VO.LimitCouponVo;
import net.messi.early.constant.IPAddress;
import net.messi.early.dto.CheckOutDTO;
import net.messi.early.mapper.*;
import net.messi.early.pojo.*;
import net.messi.early.service.CartService;
import net.messi.early.storm.http.HttpClientUtils;
import net.messi.early.utils.PriceTotal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisCluster;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service("cartService")
public class CartServiceImpl implements CartService {

    @Autowired
    private NideshopAddressMapper addressMapper;

    @Autowired
    private NideshopCouponMapper couponMapper;

    @Autowired
    private NideshopUserCouponMapper userCouponMapper;

    @Autowired
    private NideshopGoodsMapper goodsMapper;

    @Autowired
    private NideshopCartMapper cartMapper;


    @Override
    public void saveNideshopCart(NideshopCart cart) {
        //先存缓存
        //再存数据库
        try{
            cartMapper.insertSelective(cart);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public CheckOutDTO payOrderNeed(Integer addressId, Integer couponId, Integer userId, CartTotal cartTotal, List<NideshopGoods> currentCart) {
        CheckOutDTO checkOutDTO = new CheckOutDTO();
        if (addressId == 0 || addressId == null) {
            checkOutDTO.setCheckedAddress(null);
        }
        if (couponId == 0 || couponId == null) {
            checkOutDTO.setCouponPrice(0);
            checkOutDTO.setCouponList(null);
        }
        //NideshopGoods goods = goodsMapper.getGift();
        NideshopGoods goods = null;
        //超过多少赠送促销商品  ---》config
        try {
            goods = JSON.parseObject(new String(HttpClientUtils.sendGetRequest(IPAddress.JSONCONFIG+"json/salegoods.json").getBytes(),"utf-8"),NideshopGoods.class);
        }catch (Exception e){
            e.printStackTrace();
        }
        if (null != goods && cartTotal.getCheckedGoodsAmount() > 10.0f) {
            //去重
            List<String> goodsSn = new ArrayList<>();
            for (int i = 0; i < currentCart.size(); i++) {
                goodsSn.add(currentCart.get(i).getGoodsSn());
            }
            if (!goodsSn.contains(goods.getGoodsSn())) {
                goods.setSellNum(1);
                currentCart.add(goods);
            }
            cartTotal.setCheckedGoodsCount(currentCart.size()-1);
            cartTotal.setCheckedGoodsAmount(new BigDecimal(PriceTotal.totalPrice(currentCart)).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue() - goods.getRetailPrice().floatValue());
            checkOutDTO.setGoodsTotalPrice(new BigDecimal(PriceTotal.totalPrice(currentCart)).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue() - goods.getRetailPrice().floatValue());
        } else {
            for (NideshopGoods good : currentCart) {
                if (good.getGoodsSn().equals(goods.getGoodsSn())) {
                    currentCart.remove(good);
                    cartTotal.setCheckedGoodsAmount(cartTotal.getCheckedGoodsAmount() - goods.getRetailPrice().floatValue());
                }
            }
            checkOutDTO.setGoodsTotalPrice(new BigDecimal(PriceTotal.totalPrice(currentCart)).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue());
        }
        //当前购物车选中得商品
        checkOutDTO.setCheckedGoodsList(currentCart);
        //address
        if (addressId != 0 && addressId != null) {
            NideshopAddress address = addressMapper.selectByPrimaryKey(addressId);
            if (address != null) {
                checkOutDTO.setCheckedAddress(address);
            }
        }
        //actualPrice
        checkOutDTO.setActualPrice(0.5f + cartTotal.getCheckedGoodsAmount());
        CouponVo couponVo = null;
        try {
            couponVo = JSON.parseObject(new String(HttpClientUtils.sendGetRequest(IPAddress.JSONCONFIG+"json/coupon.json").getBytes(),"utf-8"), CouponVo.class);
        }catch (Exception e){
            e.printStackTrace();
        }
        //优惠卷 JSON配置  TODO get(0)
        if (cartTotal.getCheckedGoodsAmount() >= couponVo.getData().get(0).getBeyondMoney().floatValue()) {
            if (null != couponVo && couponId != 0 && couponId != null) {
                for (LimitCouponVo limitCouponVo:couponVo.getData()){
                    if (limitCouponVo.getCouponId().equals(couponId)){
                        //前端沒用checkedcoupon
                        checkOutDTO.setCheckedCoupon(null);
                        checkOutDTO.setCouponPrice(limitCouponVo.getCouponMoney().floatValue());
                        checkOutDTO.setActualPrice(checkOutDTO.getActualPrice() - limitCouponVo.getCouponMoney().floatValue());
                        break;
                    }
                }
            }
            //couponList
            //TODO sql
            if (userId != 0 && userId != null) {
                NideshopUserCouponExample userCouponExample = new NideshopUserCouponExample();
                NideshopUserCouponExample.Criteria userCouponCri = userCouponExample.createCriteria();
                userCouponCri.andUserIdEqualTo(userId);
                List<NideshopUserCoupon> userCoupons = userCouponMapper.selectByExample(userCouponExample);
                List<NideshopCoupon> couponList = new ArrayList<>();
                for (NideshopUserCoupon userCoupon : userCoupons) {
                    NideshopCoupon coupon1 = couponMapper.selectByPrimaryKey(userCoupon.getCouponId().shortValue());
                    couponList.add(coupon1);
                }
                checkOutDTO.setCouponList(couponList);
            }
        }

        //freightPrice
        checkOutDTO.setFreightPrice(0.5f);

        //orderTotalPrice
        checkOutDTO.setOrderTotalPrice(0.5f + cartTotal.getCheckedGoodsAmount());

        return checkOutDTO;
    }
}

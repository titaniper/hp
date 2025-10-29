```dbml 
// hp domain data model (inferred from existing notes) https://dbdiagram.io/d/69018c17357668b7321a0975
// Types and primary keys inferred; adjust as actual schema evolves.

Table users {
  id uuid [pk]
  email varchar [not null, unique]
  password varchar [not null]
  first_name varchar
  last_name varchar
  point int [default: 0]
}

Table sellers {
  id uuid [pk]
  name varchar
  type SellerType
  owner_id uuid [ref: > users.id]
}

Table categories {
  id uuid [pk]
  level int
  name varchar
  status varchar
  parent_id uuid [ref: - categories.id, note: 'nullable']
}

Table products {
  id uuid [pk]
  name varchar
  code varchar [unique]
  description text
  content text
  status ProductStatus
  seller_id uuid [ref: > sellers.id]
  category_id uuid [ref: > categories.id]
  original_price_amount decimal
  original_price_currency varchar
  discount_rate decimal
  version int
}

Table product_items {
  id uuid [pk]
  product_id uuid [ref: > products.id]
  name varchar
  unit_price decimal
  description text
  status ProductItemStatus
  code varchar [note: 'sku = productCode-itemCode']
  price_amount decimal
  price_unit varchar
}

Table orders {
  id uuid [pk]
  image_url varchar
  status OrderStatus
  recipient_name varchar
  order_month varchar [note: '예: YYYY-MM']
  total_price decimal
  discounted_price decimal
  final_payment_amount decimal
  delivery_fee decimal
  payment_id uuid [ref: - payments.id]
  ordered_at timestamp
  zip_code varchar
  base_address varchar
  detail_address varchar
  receiver_tel_no1 varchar
  receiver_tel_no2 varchar
  memo text
}

Table deliveries {
  id uuid [pk]
  type DeliveryType
  estimated_delivery_date date
  status DeliveryStatus
  tracking_number varchar
  delivery_fee decimal
  order_id uuid [ref: > orders.id]
}

Table order_items {
  id uuid [pk]
  order_id uuid [ref: > orders.id]
  product_id uuid [ref: > products.id, note: 'nullable']
  product_item_id uuid [ref: - product_items.id, note: 'nullable']
  price decimal
  quantity int
  product_name varchar
  unit_price decimal
  discount_price decimal
  final_price decimal
  refunded_amount decimal
}

Table order_discounts {
  id uuid [pk]
  order_id uuid [ref: > orders.id]
  type OrderDiscountType
  reference_id uuid
  price decimal
  coupon_id uuid [ref: - coupons.id, note: 'nullable']
}

Table cart_items {
  id uuid [pk]
  user_id uuid [ref: > users.id]
  product_id uuid [ref: > products.id]
  quantity int
}

Table coupon_templates {
  id uuid [pk]
  title varchar
  type CouponType
  value decimal
  status CouponTemplateStatus
  min_amount decimal [note: 'nullable']
  max_discount_amount decimal [note: 'nullable']
  total_quantity int
  issued_quantity int
  start_at timestamp [note: 'nullable']
  end_at timestamp [note: 'nullable']
}

Table coupons {
  id uuid [pk]
  user_id uuid [ref: > users.id]
  coupon_template_id uuid [ref: > coupon_templates.id, note: 'nullable']
  type CouponType
  value decimal
  expired_at timestamp [note: 'nullable']
}

Table payments {
  id uuid [pk]
  order_id uuid [ref: > orders.id]
  payment_gateway varchar
  payment_method PaymentMethod
  payment_amount decimal
  coupon_discount decimal
  instant_discount decimal
  point_used decimal
  status PaymentStatus
  payment_key varchar [note: 'nullable']
  transaction_id varchar [note: 'nullable']
  requested_at timestamp
  approved_at timestamp [note: 'nullable']
  cancelled_at timestamp [note: 'nullable']
}

Enum SellerType {
  BRAND
  PERSON
}

Enum OrderStatus {
  PENDING
  PAID
  SHIPPING
  DELIVERED
  CANCELED
  REFUNDED
}

Enum DeliveryType {
  DIRECT_DELIVERY
}

Enum DeliveryStatus {
  PREPARING
  IN_TRANSIT
  OUT_FOR_DELIVERY
  DELIVERED
  DELIVERY_FAILED
}

Enum OrderDiscountType {
  POINT
  COUPON
}

Enum ProductStatus {
  ON_SALE [note: '판매중']
}

Enum ProductItemStatus {
  ACTIVE [note: '판매중']
}

Enum CouponType {
  PERCENTAGE
  AMOUNT
  GIFT
}

Enum CouponTemplateStatus {
  DRAFT
  ACTIVE
  PAUSED
  ENDED
}

Enum PaymentMethod {
  CREDIT_CARD
  BANK_TRANSFER
  VIRTUAL_ACCOUNT
  MOBILE_PAYMENT
  COUPAY_MONEY
  POINT
}

Enum PaymentStatus {
  PENDING
  COMPLETED
  FAILED
  CANCELLED
  REFUNDED
  PARTIAL_REFUNDED
}
```

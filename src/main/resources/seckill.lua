--参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
--KEY列表
--库存key
local stockKey = "seckill:stock"..voucherId
--订单key
local orderKey = "seckill:order"..voucherId
--业务逻辑
--1。判断库存是否充足
if(tonumber(redis.call('get',stockKey)) <= 0) then
    return 1
end
--2。判断用户是否下单
if(redis.call('sismember',orderKey,userId) == 1) then
    return 2
end
--3。扣减库存
redis.call('incrby',stockKey,-1)
--4。创建订单
redis.call('sadd',orderKey,userId)
--向消息队列里发送信息
redis.call('xadd','stream.orders','*','voucherId',voucherId,'userId',userId,'id',orderId);
return 0

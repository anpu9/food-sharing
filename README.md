# 🥘food-sharing

Project Description: This project is aimed at implementing various features using Redis as the underlying technology. It covers functionalities like SMS login, merchant query caching, coupon seckill, nearby merchants, UV statistics, user check-ins, friend followings, and expert store exploration. Each feature utilizes different Redis data structures and concepts to achieve its objective.

## Features

1. SMS Login
   - Description: Allows users to log in using SMS verification.
   - Implementation: Redis shared session is used to maintain user session data.

2. Merchant Query Caching
   - Description: Implements caching mechanism for merchant queries.
   - Concepts Covered: Caching techniques, cache penetration, cache breakdown, cache avalanche.
   - Implementation: Demonstrates practical scenarios related to caching concepts using Redis.

3. Coupon Seckill
   - Description: Provides coupon seckill functionality.
   - Concepts Covered: Redis counters, high-performance Redis operations with Lua scripts, Redis distributed locks, Redis message queues.
   - Implementation: Utilizes Redis counters and Lua scripting for efficient coupon seckill operations. Implements distributed locks and message queues with Redis.

4. Nearby Merchants
   - Description: Enables searching for nearby merchants based on geographic coordinates.
   - Concepts Covered: GEOHash in Redis for geospatial operations.
   - Implementation: Utilizes Redis GEOHash to perform geospatial operations and find nearby merchants.

5. UV Statistics
   - Description: Performs user visit (UV) statistics.
   - Implementation: Utilizes Redis for efficient tracking and counting of unique user visits.

6. User Check-Ins
   - Description: Implements user check-in functionality.
   - Concepts Covered: Redis BitMap data structure.
   - Implementation: Uses Redis BitMap to store and analyze user check-in data efficiently.

7. Friend Followings
   - Description: Implements friend following functionality.
   - Concepts Covered: Set operations for following, unfollowing, and common followings.
   - Implementation: Utilizes Redis Set data structure to manage friend followings.

8. Expert Store Exploration
   - Description: Provides functionalities related to expert store exploration.
   - Concepts Covered: List operations for managing like lists, SortedSet operations for leaderboard functionalities.
   - Implementation: Uses Redis List to manage like lists and SortedSet to implement leaderboard functionalities.

## Getting Started

To run the project locally, please follow these steps:

1. Clone the repository: `git clone https://github.com/your/repository.git`
2. Install the necessary dependencies.
3. Configure the Redis connection settings.
4. Build and run the application.
5. Access the application through the specified endpoints.

## Dependencies

Redis: Version X.X.X
MySQL Connector/J: Version 5.1.47
Spring Boot Starter Data Redis
Apache Commons Pool 2
Spring Boot Starter Web
Project Lombok
Spring Boot Starter Test
MyBatis Plus Boot Starter
Hutool: Version 5.7.17

## Usage

1. SMS Login: Access the login page and follow the SMS verification process.
2. Merchant Query Caching: Perform merchant queries and observe the caching behavior.
3. Coupon Seckill: Participate in coupon seckill events and observe the high-performance Redis operations.
4. Nearby Merchants: Use the provided geographic coordinates to search for nearby merchants.
5. UV Statistics: Track and view unique user visits.
6. User Check-Ins: Perform user check-ins and view the statistics.
7. Friend Followings: Follow, unfollow, and observe common followings with friends.
8. Expert Store Exploration: Like stores, view like lists, and check leaderboard rankings.

## Contributing

Contributions are welcome! If you have any suggestions, improvements, or bug fixes, please open an issue or submit a pull request.

## License

This project is licensed under the [MIT License](https://opensource.org/licenses/MIT).

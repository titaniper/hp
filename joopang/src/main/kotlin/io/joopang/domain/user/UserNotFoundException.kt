package io.joopang.domain.user

class UserNotFoundException(userId: String) :
    RuntimeException("User with id $userId was not found")

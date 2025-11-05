package io.joopang.services.user.domain

class UserNotFoundException(userId: String) :
    RuntimeException("User with id $userId was not found")

package com.komica.reader.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.Resource
import com.komica.reader.repository.KomicaRepository
import com.komica.reader.util.FavoritesManager
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    lateinit var repository: KomicaRepository

    @MockK
    lateinit var favoritesManager: FavoritesManager

    @MockK
    lateinit var application: Application

    private lateinit var viewModel: MainViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        // Inject mocks via constructor
        viewModel = MainViewModel(application, repository, favoritesManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadBoards success updates categories`() = runTest {
        // Given
        val mockCategories = listOf(BoardCategory("Test Category", emptyList()))
        coEvery { repository.fetchBoards(any()) } returns Resource.Success(mockCategories)
        every { favoritesManager.isFavorite(any()) } returns false
        
        val observer = mockk<Observer<List<BoardCategory>>>(relaxed = true)
        viewModel.categories.observeForever(observer)

        // When
        viewModel.loadBoards()

        // Then
        verify { observer.onChanged(match { it.size == 1 && it[0].name == "Test Category" }) }
    }

    @Test
    fun `loadBoards error updates errorMessage`() = runTest {
        // Given
        val errorMsg = "Network Error"
        coEvery { repository.fetchBoards(any()) } returns Resource.Error(Exception(errorMsg))
        
        val observer = mockk<Observer<String?>>(relaxed = true)
        viewModel.errorMessage.observeForever(observer)

        // When
        viewModel.loadBoards()

        // Then
        verify { observer.onChanged(match { it?.contains(errorMsg) == true }) }
    }
}
package com.example.melodist.ui.components

import androidx.compose.runtime.Composable

@Composable
fun AlbumScreenSkeleton() = ProvideShimmerTransition { AlbumScreenSkeletonContent() }

@Composable
fun PlaylistScreenSkeleton() = ProvideShimmerTransition { PlaylistScreenSkeletonContent() }

@Composable
fun ArtistScreenSkeleton() = ProvideShimmerTransition { ArtistScreenSkeletonContent() }
